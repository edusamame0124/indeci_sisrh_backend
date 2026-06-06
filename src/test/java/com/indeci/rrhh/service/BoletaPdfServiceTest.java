package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MovimientoPlanillaDetalleResponseDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Spec 011 / B1 — Tests del generador de boleta PDF (M06).
 *   - genera un PDF válido cuando hay planilla
 *   - sin planilla del período → NegocioException
 *   - sin datos de persona → usa "Empleado {id}" y aun así genera el PDF
 */
@ExtendWith(MockitoExtension.class)
class BoletaPdfServiceTest {

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private MovimientoPlanillaDetalleService detalleService;
    @Mock private PersonaService personaService;

    @InjectMocks private BoletaPdfService service;

    private static final Long EMPLEADO_ID = 42L;
    private static final String PERIODO = "2026-05";

    private MovimientoPlanilla movimiento() {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(100L);
        m.setEmpleadoId(EMPLEADO_ID);
        m.setPeriodo(PERIODO);
        m.setTotalIngresos(3000.0);
        m.setTotalDescuentos(450.0);
        m.setNetoPagar(2550.0);
        m.setActivo(1);
        return m;
    }

    private MovimientoPlanillaDetalleResponseDto detalle(String concepto, String tipo, double monto) {
        MovimientoPlanillaDetalleResponseDto d = new MovimientoPlanillaDetalleResponseDto();
        d.setConcepto(concepto);
        d.setTipoConcepto(tipo);
        d.setMonto(monto);
        return d;
    }

    private PersonaResumenDto persona() {
        PersonaResumenDto p = new PersonaResumenDto();
        p.setEmpleadoId(EMPLEADO_ID);
        p.setNombreCompleto("Ana Lopez");
        p.setDni("12345678");
        return p;
    }

    @Test
    void generar_caso_feliz_produce_un_pdf_valido() {
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(movimiento()));
        when(detalleService.listarDetalle(EMPLEADO_ID, PERIODO))
                .thenReturn(List.of(
                        detalle("Sueldo básico", "INGRESO", 3000.0),
                        detalle("Aporte ONP", "DESCUENTO", 390.0)));
        when(personaService.listar()).thenReturn(List.of(persona()));

        byte[] pdf = service.generar(EMPLEADO_ID, PERIODO);

        assertThat(pdf).isNotEmpty();
        // La firma de todo PDF es la cadena "%PDF" en los primeros bytes.
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void generar_sin_planilla_lanza_negocio() {
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generar(EMPLEADO_ID, PERIODO))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("planilla");
    }

    @Test
    void generar_sin_persona_usa_fallback_y_genera_pdf() {
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(movimiento()));
        when(detalleService.listarDetalle(EMPLEADO_ID, PERIODO))
                .thenReturn(List.of());
        when(personaService.listar()).thenReturn(List.of()); // sin persona

        byte[] pdf = service.generar(EMPLEADO_ID, PERIODO);

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void generar_con_aportes_empleador_produce_pdf_mas_grande_que_sin_aportes() {
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(movimiento()));
        when(personaService.listar()).thenReturn(List.of(persona()));

        when(detalleService.listarDetalle(EMPLEADO_ID, PERIODO))
                .thenReturn(List.of(
                        detalle("Remuneración CAS", "INGRESO", 6000.0),
                        detalle("Aporte AFP 10%", "DESCUENTO", 600.0)));
        byte[] sinAportes = service.generar(EMPLEADO_ID, PERIODO);

        when(detalleService.listarDetalle(EMPLEADO_ID, PERIODO))
                .thenReturn(List.of(
                        detalle("Remuneración CAS", "INGRESO", 6000.0),
                        detalle("Aporte AFP 10%", "DESCUENTO", 600.0),
                        detalle("ESSALUD 9% (Empleador)", "APORTE", 540.0)));
        byte[] conAportes = service.generar(EMPLEADO_ID, PERIODO);

        assertThat(new String(conAportes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(conAportes.length).isGreaterThan(sinAportes.length);
    }
}
