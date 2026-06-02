package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ReporteEvolucionDto;
import com.indeci.rrhh.dto.ReporteEvolucionDto.ReporteEvolucionItemDto;
import com.indeci.rrhh.dto.ReporteRegimenDto;
import com.indeci.rrhh.dto.ReporteTopConceptosDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

/**
 * F3.5c — Tests del Tablero Consolidado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteConsolidadoServiceTest {

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private MovimientoPlanillaDetalleRepository detalleRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;

    @InjectMocks private ReporteConsolidadoService service;

    // ==================== Validaciones ====================

    @Test
    void evolucion_periodo_nulo_lanza() {
        assertThatThrownBy(() -> service.evolucion(null, 3))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void evolucion_meses_fuera_de_rango_lanza() {
        assertThatThrownBy(() -> service.evolucion("2026-05", 0))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("ventana");
        assertThatThrownBy(() -> service.evolucion("2026-05", 25))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void top_conceptos_limite_fuera_de_rango_lanza() {
        assertThatThrownBy(() -> service.topConceptos("2026-05", 0))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("límite");
    }

    // ==================== EVOLUCIÓN ====================

    @Test
    void evolucion_3_meses_construye_rango_correcto_y_calcula_delta() {
        // periodoBase = 2026-05, 3 meses → 2026-03, 2026-04, 2026-05.
        when(movimientoRepository.findByPeriodoAndActivo("2026-03", 1))
                .thenReturn(List.of(mov(1L, 1000.0, 200.0, 800.0, "BIEN")));
        when(movimientoRepository.findByPeriodoAndActivo("2026-04", 1))
                .thenReturn(List.of(mov(2L, 1100.0, 200.0, 900.0, "BIEN")));
        when(movimientoRepository.findByPeriodoAndActivo("2026-05", 1))
                .thenReturn(List.of(
                        mov(3L, 1200.0, 200.0, 1000.0, "BIEN"),
                        mov(4L, 1200.0, 300.0, 900.0, "NETO_NO_VA")));
        when(conceptoRepository.findByActivo(1)).thenReturn(List.of());
        lenient().when(detalleRepository.findByMovimientoPlanillaId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());

        ReporteEvolucionDto r = service.evolucion("2026-05", 3);

        assertThat(r.items()).hasSize(3);
        assertThat(r.items()).extracting(ReporteEvolucionItemDto::periodo)
                .containsExactly("2026-03", "2026-04", "2026-05");
        assertThat(r.items().get(0).deltaPctNetoVsAnterior()).isNull();
        assertThat(r.items().get(1).deltaPctNetoVsAnterior().doubleValue()).isEqualTo(12.50);
        // 800 + 900 + 1900 = 3600
        assertThat(r.totalNetoAcumulado().doubleValue()).isEqualTo(3600.00);
        assertThat(r.items().get(2).conteoNetoNoVa()).isEqualTo(1);
    }

    @Test
    void evolucion_sin_movimientos_devuelve_ceros() {
        when(movimientoRepository.findByPeriodoAndActivo(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(1))).thenReturn(List.of());
        when(conceptoRepository.findByActivo(1)).thenReturn(List.of());

        ReporteEvolucionDto r = service.evolucion("2026-05", 3);

        assertThat(r.items()).hasSize(3);
        assertThat(r.totalNetoAcumulado().doubleValue()).isEqualTo(0.00);
        assertThat(r.promedioMensual().doubleValue()).isEqualTo(0.00);
    }

    // ==================== RÉGIMEN ====================

    @Test
    void distribucion_regimen_agrupa_y_calcula_porcentajes() {
        when(regimenLaboralRepository.findAll()).thenReturn(List.of(
                regimen(1L, "728", "DL 728"),
                regimen(2L, "CAS", "DL 1057")));
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                planilla(10L, 1L), planilla(11L, 1L), planilla(12L, 2L)));
        when(movimientoRepository.findByPeriodoAndActivo("2026-05", 1)).thenReturn(List.of(
                mov(10L, 2000.0, 300.0, 1700.0, "BIEN"), // 728
                mov(11L, 2200.0, 400.0, 1800.0, "BIEN"), // 728
                mov(12L, 1500.0, 200.0, 1300.0, "BIEN")  // CAS
        ));

        ReporteRegimenDto r = service.distribucionRegimen("2026-05");

        assertThat(r.totalEmpleados()).isEqualTo(3);
        assertThat(r.totalNeto().doubleValue()).isEqualTo(4800.00);
        // Orden desc por neto: 728 (3500) > CAS (1300).
        assertThat(r.items()).hasSize(2);
        assertThat(r.items().get(0).regimenCodigo()).isEqualTo("728");
        assertThat(r.items().get(0).conteoEmpleados()).isEqualTo(2);
        assertThat(r.items().get(0).totalNeto().doubleValue()).isEqualTo(3500.00);
        assertThat(r.items().get(0).porcentajeTotal().doubleValue()).isEqualTo(72.92);
        assertThat(r.items().get(0).netoPromedio().doubleValue()).isEqualTo(1750.00);
        assertThat(r.items().get(1).regimenCodigo()).isEqualTo("CAS");
        assertThat(r.items().get(1).porcentajeTotal().doubleValue()).isEqualTo(27.08);
    }

    // ==================== TOP CONCEPTOS ====================

    @Test
    void top_conceptos_respeta_limite_y_ordena_desc() {
        // 4 conceptos en BD; pedimos top 2.
        ConceptoPlanilla c1 = concepto(100L, "00001", "Sueldo básico", "REMUNERATIVO");
        ConceptoPlanilla c2 = concepto(101L, "00302", "Asig. familiar", "REMUNERATIVO");
        ConceptoPlanilla c3 = concepto(102L, "05001", "Aporte ONP", "APORTE_TRABAJADOR");
        ConceptoPlanilla c4 = concepto(103L, "06001", "ESSALUD 9%", "APORTE_EMPLEADOR");
        when(conceptoRepository.findByActivo(1)).thenReturn(List.of(c1, c2, c3, c4));

        // 2 empleados con movimiento, cada uno con 4 detalles.
        MovimientoPlanilla m1 = mov(1L, 2000.0, 0.0, 0.0, "BIEN"); m1.setId(1L);
        MovimientoPlanilla m2 = mov(2L, 2000.0, 0.0, 0.0, "BIEN"); m2.setId(2L);
        when(movimientoRepository.findByPeriodoAndActivo("2026-05", 1))
                .thenReturn(List.of(m1, m2));

        when(detalleRepository.findByMovimientoPlanillaId(1L)).thenReturn(List.of(
                det(100L, 1500.0), det(101L, 200.0), det(102L, 260.0), det(103L, 180.0)));
        when(detalleRepository.findByMovimientoPlanillaId(2L)).thenReturn(List.of(
                det(100L, 1800.0), det(101L, 200.0), det(102L, 260.0), det(103L, 180.0)));

        ReporteTopConceptosDto r = service.topConceptos("2026-05", 2);

        assertThat(r.items()).hasSize(2);
        // Sueldo básico = 1500+1800=3300 > ONP=520 > ESSALUD=360 > Asig=400.
        // Top 2 → sueldo básico, asig familiar (400) — espera asig=400 > onp=520? No: 520>400.
        // Recalculo: sueldo 3300, ONP 520, Asig 400, ESSALUD 360. Top 2 = sueldo, ONP.
        assertThat(r.items().get(0).codigoMef()).isEqualTo("00001");
        assertThat(r.items().get(0).montoTotal().doubleValue()).isEqualTo(3300.00);
        assertThat(r.items().get(0).conteoEmpleados()).isEqualTo(2);
        assertThat(r.items().get(1).codigoMef()).isEqualTo("05001");
        assertThat(r.items().get(1).montoTotal().doubleValue()).isEqualTo(520.00);
        // % sobre totalIngresos del período (2000+2000=4000).
        assertThat(r.totalIngresosPeriodo().doubleValue()).isEqualTo(4000.00);
        assertThat(r.items().get(0).porcentajeIngresos().doubleValue()).isEqualTo(82.50);
    }

    // ==================== HELPERS ====================

    private MovimientoPlanilla mov(Long empId, Double ing, Double desc, Double neto, String estadoNeto) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setEmpleadoId(empId);
        m.setTotalIngresos(ing);
        m.setTotalDescuentos(desc);
        m.setNetoPagar(neto);
        m.setEstadoNeto(estadoNeto);
        m.setActivo(1);
        return m;
    }

    private MovimientoPlanillaDetalle det(Long conceptoId, Double monto) {
        MovimientoPlanillaDetalle d = new MovimientoPlanillaDetalle();
        d.setConceptoPlanillaId(conceptoId);
        d.setMonto(monto);
        return d;
    }

    private EmpleadoPlanilla planilla(Long empId, Long regimenId) {
        EmpleadoPlanilla pl = new EmpleadoPlanilla();
        pl.setId(empId);
        pl.setEmpleadoId(empId);
        pl.setActivo(1);
        pl.setRegimenLaboralId(regimenId);
        return pl;
    }

    private RegimenLaboral regimen(Long id, String codigo, String nombre) {
        RegimenLaboral r = new RegimenLaboral();
        r.setId(id);
        r.setCodigo(codigo);
        r.setNombre(nombre);
        r.setActivo(1);
        return r;
    }

    private ConceptoPlanilla concepto(Long id, String mef, String nombre, String tipoConcepto) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(id);
        c.setCodigoMef(mef);
        c.setNombre(nombre);
        c.setTipoConcepto(tipoConcepto);
        c.setActivo(1);
        return c;
    }
}
