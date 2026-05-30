package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.CatSuspensionSunat;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Entidad;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.Suspension;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.CatSuspensionSunatRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EntidadRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.SuspensionRepository;

/**
 * B3 / M09 — Tests de PlameService (.rem consolidación, .jor horas, .snl exclusión cód 21).
 */
@ExtendWith(MockitoExtension.class)
class PlameServiceTest {

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private MovimientoPlanillaDetalleRepository detalleRepository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;
    @Mock private AsistenciaCabeceraRepository asistenciaRepository;
    @Mock private SuspensionRepository suspensionRepository;
    @Mock private CatSuspensionSunatRepository catSuspensionRepository;
    @Mock private EntidadRepository entidadRepository;
    @Mock private PersonaService personaService;

    @InjectMocks private PlameService service;

    private ConceptoPlanilla concepto(long id, String plame, String tipo) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(id);
        c.setCodigoPlameSunat(plame);
        c.setTipo(tipo);
        return c;
    }

    private MovimientoPlanillaDetalle det(long conceptoId, double monto) {
        MovimientoPlanillaDetalle d = new MovimientoPlanillaDetalle();
        d.setConceptoPlanillaId(conceptoId);
        d.setMonto(monto);
        return d;
    }

    private MovimientoPlanilla mov(long id, long empId) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(id);
        m.setEmpleadoId(empId);
        return m;
    }

    @BeforeEach
    void comun() {
        Entidad ent = new Entidad();
        ent.setCodEntidad("000009");
        ent.setRuc("20135890031");
        lenient().when(entidadRepository.findAll()).thenReturn(List.of(ent));

        PersonaEmpleadoResponseDto persona = new PersonaEmpleadoResponseDto();
        persona.setEmpleadoId(1L);
        persona.setDni("00256418");
        lenient().when(personaService.listar()).thenReturn(List.of(persona));
    }

    @Test
    void remConsolidaPorDniYCodigoYNombraDeterministico() {
        when(conceptoRepository.findAll()).thenReturn(List.of(
                concepto(10L, "0601", "INGRESO"),
                concepto(11L, "0608", "DESCUENTO")));
        when(movimientoRepository.findByPeriodoAndActivo("2026-03", 1))
                .thenReturn(List.of(mov(100L, 1L)));
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of(
                det(10L, 2000.00), det(10L, 264.19), det(11L, 236.42)));

        PlameService.PlameArchivo out = service.generarRem("2026-03");

        assertThat(out.nombreArchivo()).isEqualTo("060120260320135890031.rem");
        // 0601 consolidado 2000.00 + 264.19 = 2264.19; orden por código asc.
        assertThat(out.contenido()).isEqualTo(
                "01|00256418|0601|2264.19|2264.19|\r\n"
                + "01|00256418|0608|236.42|236.42|\r\n");
        assertThat(out.totalIngresos()).isEqualByComparingTo("2264.19");
        assertThat(out.totalDescuentos()).isEqualByComparingTo("236.42");
        assertThat(out.nroLineas()).isEqualTo(2);
    }

    @Test
    void jorUsaDiasLaboradosPor8() {
        AsistenciaCabecera asis = new AsistenciaCabecera();
        asis.setEmpleadoId(1L);
        asis.setDiasLaborados(22);
        when(asistenciaRepository.findByPeriodoAndActivo("2026-03", 1)).thenReturn(List.of(asis));
        when(movimientoRepository.findByPeriodoAndActivo("2026-03", 1))
                .thenReturn(List.of(mov(100L, 1L)));

        PlameService.PlameArchivo out = service.generarJor("2026-03");

        // 22 × 8 = 176
        assertThat(out.contenido()).isEqualTo("01|00256418|176|0|0|0|\r\n");
        assertThat(out.nombreArchivo()).isEqualTo("060120260320135890031.jor");
    }

    @Test
    void jorSinAsistenciaUsaMesCompleto176() {
        when(asistenciaRepository.findByPeriodoAndActivo("2026-03", 1)).thenReturn(List.of());
        when(movimientoRepository.findByPeriodoAndActivo("2026-03", 1))
                .thenReturn(List.of(mov(100L, 1L)));

        PlameService.PlameArchivo out = service.generarJor("2026-03");

        assertThat(out.contenido()).isEqualTo("01|00256418|176|0|0|0|\r\n");
    }

    @Test
    void snlIncluyeSubsidiadoYExcluyeLactanciaCod21() {
        CatSuspensionSunat cat03 = new CatSuspensionSunat();
        cat03.setCodSuspension("03");
        cat03.setVaEnSnl("S");
        CatSuspensionSunat cat21 = new CatSuspensionSunat();
        cat21.setCodSuspension("21");
        cat21.setVaEnSnl("N");
        when(catSuspensionRepository.findAll()).thenReturn(List.of(cat03, cat21));

        Suspension descanso = new Suspension();
        descanso.setEmpleadoId(1L);
        descanso.setCodSuspension("03");
        descanso.setDiasAfectos(5);
        Suspension lactancia = new Suspension();
        lactancia.setEmpleadoId(1L);
        lactancia.setCodSuspension("21");
        lactancia.setDiasAfectos(0);
        when(suspensionRepository.findByEstadoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                "ACTIVO", LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)))
                .thenReturn(List.of(descanso, lactancia));

        PlameService.PlameArchivo out = service.generarSnl("2026-03");

        // Solo el descanso médico (cód 03); lactancia (cód 21) excluida.
        assertThat(out.contenido()).isEqualTo("1|00256418|03|5|\r\n");
        assertThat(out.nroLineas()).isEqualTo(1);
        assertThat(out.nombreArchivo()).isEqualTo("060120260320135890031.snl");
    }
}
