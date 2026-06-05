package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.entity.AbonoBanco;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.repository.AbonoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

/**
 * Spec 013 / C1 · P-07 — Tests del archivo bancario TXT + ZIP.
 *   - ZIP con un .txt por banco, nombrado ABONO_[BANCO]_[YYYYMM].txt
 *   - cada línea = 10 campos tab-separados con NRO_ORDEN correlativo
 *   - período sin abonos → NegocioException (caso de error)
 *   - abono "SIN CUENTA" se excluye del archivo (caso de borde)
 */
@ExtendWith(MockitoExtension.class)
class ArchivoBancoServiceTest {

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private AbonoBancoRepository abonoRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private PersonaService personaService;

    @InjectMocks private ArchivoBancoService service;

    private static final String PER = "2026-05";

    private MovimientoPlanilla mov(Long id) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(id);
        return m;
    }

    private AbonoBanco abono(Long empleadoId, String banco, double neto) {
        AbonoBanco a = new AbonoBanco();
        a.setEmpleadoId(empleadoId);
        a.setBanco(banco);
        a.setNroCuenta("CTA" + empleadoId);
        a.setCci("CCI" + empleadoId);
        a.setMeta("0056");
        a.setMontoNeto(neto);
        return a;
    }

    private PersonaResumenDto persona(Long empleadoId, String dni, String nombre) {
        PersonaResumenDto p = new PersonaResumenDto();
        p.setEmpleadoId(empleadoId);
        p.setDni(dni);
        p.setNombreCompleto(nombre);
        return p;
    }

    private EmpleadoPlanilla planilla(String codigoAirhsp) {
        EmpleadoPlanilla ep = new EmpleadoPlanilla();
        ep.setCodigoAirhsp(codigoAirhsp);
        return ep;
    }

    /** Lee el ZIP en un mapa nombre-de-archivo → contenido. */
    private Map<String, String> leerZip(byte[] zip) throws Exception {
        Map<String, String> entradas = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entradas.put(e.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entradas;
    }

    @Test
    void generaUnArchivoPorBancoNombradoConPeriodo() throws Exception {
        when(movimientoRepository.findByPeriodoAndActivo(PER, 1))
                .thenReturn(List.of(mov(100L), mov(200L)));
        when(abonoRepository.findByMovimientoPlanillaId(100L))
                .thenReturn(List.of(abono(10L, "BCP", 900d)));
        when(abonoRepository.findByMovimientoPlanillaId(200L))
                .thenReturn(List.of(abono(20L, "BANCO DE LA NACION", 1800d)));
        when(personaService.listar()).thenReturn(List.of(
                persona(10L, "44556677", "Ana Lopez"),
                persona(20L, "11223344", "Beto Diaz")));
        lenient().when(planillaRepository.findFirstByEmpleadoIdAndActivo(10L, 1))
                .thenReturn(Optional.of(planilla("000139")));
        lenient().when(planillaRepository.findFirstByEmpleadoIdAndActivo(20L, 1))
                .thenReturn(Optional.of(planilla("000140")));

        Map<String, String> zip = leerZip(service.generarZip(PER));

        assertThat(zip).containsKeys(
                "ABONO_BCP_202605.txt",
                "ABONO_BANCO_DE_LA_NACION_202605.txt");
    }

    @Test
    void cadaLineaTieneDiezCamposTabuladosConOrdenCorrelativo() throws Exception {
        when(movimientoRepository.findByPeriodoAndActivo(PER, 1))
                .thenReturn(List.of(mov(100L)));
        when(abonoRepository.findByMovimientoPlanillaId(100L))
                .thenReturn(List.of(abono(10L, "BCP", 900d), abono(11L, "BCP", 500d)));
        when(personaService.listar()).thenReturn(List.of(
                persona(10L, "44556677", "Ana Lopez"),
                persona(11L, "99887766", "Zoe Quispe")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(10L, 1))
                .thenReturn(Optional.of(planilla("000139")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(11L, 1))
                .thenReturn(Optional.of(planilla("000200")));

        String txt = leerZip(service.generarZip(PER)).get("ABONO_BCP_202605.txt");
        String[] lineas = txt.split("\r\n");

        assertThat(lineas).hasSize(2);
        String[] campos = lineas[0].split("\t", -1);
        assertThat(campos).hasSize(10);
        assertThat(campos[0]).isEqualTo("1");          // NRO_ORDEN
        assertThat(campos[1]).isEqualTo("BCP");        // BANCO
        assertThat(campos[5]).isEqualTo("Ana Lopez");  // APELLIDOS_NOMBRES (orden alfabético)
        assertThat(campos[8]).isEqualTo("900.00");     // MONTO_NETO
        assertThat(campos[9]).isEmpty();               // NRO_TICKET_MCPP vacío
        assertThat(lineas[1].split("\t", -1)[0]).isEqualTo("2");
    }

    @Test
    void periodoSinAbonosLanzaNegocioException() {
        when(movimientoRepository.findByPeriodoAndActivo(PER, 1)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generarZip(PER))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("no tiene abonos");
    }

    @Test
    void excluyeAbonosSinCuentaBancaria() throws Exception {
        when(movimientoRepository.findByPeriodoAndActivo(PER, 1))
                .thenReturn(List.of(mov(100L)));
        when(abonoRepository.findByMovimientoPlanillaId(100L))
                .thenReturn(List.of(abono(10L, "BCP", 900d), abono(20L, "SIN CUENTA", 0d)));
        when(personaService.listar()).thenReturn(List.of(
                persona(10L, "44556677", "Ana Lopez")));
        lenient().when(planillaRepository.findFirstByEmpleadoIdAndActivo(10L, 1))
                .thenReturn(Optional.of(planilla("000139")));

        Map<String, String> zip = leerZip(service.generarZip(PER));

        assertThat(zip).containsOnlyKeys("ABONO_BCP_202605.txt");
    }
}
