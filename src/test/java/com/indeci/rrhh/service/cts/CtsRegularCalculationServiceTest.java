package com.indeci.rrhh.service.cts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.indeci.rrhh.dto.DiasNoComputablesDto;
import com.indeci.rrhh.dto.cts.CtsRegularResultDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PlanillaLote;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PlanillaLoteRepository;
import com.indeci.rrhh.service.GeneradorPlanillaService;
import com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta;

/**
 * PLAN_LSG_CTS_TRUNCA_Y_REGULAR — Parte B. La CTS Regular pasa de fórmula plana
 * ({@code base/2}) a prorrateo real por tiempo computable del semestre (D-1: ancla al
 * ingreso; D-2: neto de LSG + faltas, Art. 8 TUO Ley de CTS).
 */
class CtsRegularCalculationServiceTest {

    private static final Long EMPLEADO_ID = 20L;
    private static final Long REGIMEN_276 = 1L;
    private static final Long REGIMEN_CAS = 9L;

    private EmpleadoPlanillaRepository planillaRepository;
    private PlanillaLoteRepository planillaLoteRepository;
    private MovimientoPlanillaRepository movimientoRepository;
    private MovimientoPlanillaDetalleRepository detalleRepository;
    private ConceptoPlanillaRepository conceptoRepository;
    private GeneradorPlanillaService motor;
    private IncidenciaLaboralCompuesta incidenciaLaboralCompuesta;
    private CtsRegularCalculationService service;

    @BeforeEach
    void setUp() {
        planillaRepository = mock(EmpleadoPlanillaRepository.class);
        planillaLoteRepository = mock(PlanillaLoteRepository.class);
        movimientoRepository = mock(MovimientoPlanillaRepository.class);
        detalleRepository = mock(MovimientoPlanillaDetalleRepository.class);
        conceptoRepository = mock(ConceptoPlanillaRepository.class);
        motor = mock(GeneradorPlanillaService.class);
        incidenciaLaboralCompuesta = mock(IncidenciaLaboralCompuesta.class);

        service = new CtsRegularCalculationService(
                planillaRepository, planillaLoteRepository, movimientoRepository,
                detalleRepository, conceptoRepository, motor,
                new CtsTiempoServiciosCalculator(), incidenciaLaboralCompuesta);

        when(planillaLoteRepository.findByPeriodoAndRegimenLaboralCodigoAndTipoPlanillaAndCorrelativo(
                any(), any(), any(), eq(1))).thenReturn(Optional.empty());
        when(planillaLoteRepository.save(any(PlanillaLote.class))).thenAnswer(inv -> {
            PlanillaLote l = inv.getArgument(0);
            if (l.getId() == null) l.setId(900L);
            return l;
        });
        when(movimientoRepository.save(any(MovimientoPlanilla.class))).thenAnswer(inv -> {
            MovimientoPlanilla m = inv.getArgument(0);
            if (m.getId() == null) m.setId(500L);
            return m;
        });
        when(conceptoRepository.findByCodigoAndActivo(eq("CTS_REGULAR"), eq(1)))
                .thenReturn(Optional.of(new ConceptoPlanilla()));
        // Por defecto sin incidencias (cero regresión).
        when(incidenciaLaboralCompuesta.calcularDesglose(any(), any(), any()))
                .thenReturn(DiasNoComputablesDto.cero());
    }

    private EmpleadoPlanilla vinculo(Long regimenId, double sueldo, LocalDate ingreso) {
        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setId(300L);
        v.setEmpleadoId(EMPLEADO_ID);
        v.setRegimenLaboralId(regimenId);
        v.setSueldoBasico(sueldo);
        v.setFechaInicioContrato(ingreso);
        v.setActivo(1);
        return v;
    }

    private ArgumentCaptor<MovimientoPlanilla> capturarMovimiento() {
        ArgumentCaptor<MovimientoPlanilla> capt = ArgumentCaptor.forClass(MovimientoPlanilla.class);
        org.mockito.Mockito.verify(movimientoRepository).save(capt.capture());
        return capt;
    }

    // ── Caso feliz: semestre completo, sin incidencias (6 meses, 0 días) ──
    @Test
    void semestre_completo_sin_incidencias_paga_6_de_12() {
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                vinculo(REGIMEN_276, 12000.0, LocalDate.of(2020, 1, 1)))); // ingreso muy anterior al semestre
        when(motor.resolverRegimenLaboralCodigo(REGIMEN_276)).thenReturn("276");
        when(motor.resolverBaseRemunerativa(any(), eq("2026-05"))).thenReturn(new BigDecimal("12000"));

        CtsRegularResultDto r = service.generarCts("2026-05", null);

        assertThat(r.exitosos()).isEqualTo(1);
        MovimientoPlanilla mov = capturarMovimiento().getValue();
        // 12000/12 * 6 meses = 6000.00 ; sin días sueltos.
        assertThat(mov.getNetoPagar()).isCloseTo(6000.00, within(0.01));
        assertThat(mov.getObservacion()).isEqualTo("Tiempo Efectivo: 6m 0d");
    }

    // ── Caso normativo: LSG reduce el tiempo computable (ejemplo confirmado por RR.HH.) ──
    @Test
    void lsg_de_5_dias_reduce_a_5_meses_25_dias() {
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                vinculo(REGIMEN_276, 12000.0, LocalDate.of(2020, 1, 1))));
        when(motor.resolverRegimenLaboralCodigo(REGIMEN_276)).thenReturn("276");
        when(motor.resolverBaseRemunerativa(any(), eq("2026-05"))).thenReturn(new BigDecimal("12000"));
        when(incidenciaLaboralCompuesta.calcularDesglose(eq(EMPLEADO_ID), any(), any()))
                .thenReturn(DiasNoComputablesDto.of(5, 0));

        CtsRegularResultDto r = service.generarCts("2026-05", null);

        assertThat(r.exitosos()).isEqualTo(1);
        MovimientoPlanilla mov = capturarMovimiento().getValue();
        // (1000*5) + (1000/30*25) = 5000.00 + 833.33 = 5833.33
        assertThat(mov.getNetoPagar()).isCloseTo(5833.33, within(0.01));
        assertThat(mov.getObservacion()).isEqualTo("Tiempo Efectivo: 5m 25d (Días descontados: LSG 5d)");
    }

    // ── Desglose LSG + faltas combinado en la observación (Transparencia UI/UX) ──
    @Test
    void lsg_y_faltas_combinadas_se_desglosan_en_la_observacion() {
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                vinculo(REGIMEN_276, 12000.0, LocalDate.of(2020, 1, 1))));
        when(motor.resolverRegimenLaboralCodigo(REGIMEN_276)).thenReturn("276");
        when(motor.resolverBaseRemunerativa(any(), eq("2026-05"))).thenReturn(new BigDecimal("12000"));
        when(incidenciaLaboralCompuesta.calcularDesglose(eq(EMPLEADO_ID), any(), any()))
                .thenReturn(DiasNoComputablesDto.of(3, 2));

        service.generarCts("2026-05", null);

        MovimientoPlanilla mov = capturarMovimiento().getValue();
        assertThat(mov.getObservacion())
                .isEqualTo("Tiempo Efectivo: 5m 25d (Días descontados: LSG 3d, Faltas 2d)");
    }

    // ── Decisión 1: ingreso a mitad del semestre — prorrateo anclado ──
    @Test
    void ingreso_a_mitad_de_semestre_prorratea_desde_el_ingreso() {
        // Semestre Nov-Abr; ingresó el 01/02 → 3 meses (Feb, Mar, Abr).
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                vinculo(REGIMEN_276, 12000.0, LocalDate.of(2026, 2, 1))));
        when(motor.resolverRegimenLaboralCodigo(REGIMEN_276)).thenReturn("276");
        when(motor.resolverBaseRemunerativa(any(), eq("2026-05"))).thenReturn(new BigDecimal("12000"));

        CtsRegularResultDto r = service.generarCts("2026-05", null);

        MovimientoPlanilla mov = capturarMovimiento().getValue();
        // 1000 * 3 meses = 3000.00
        assertThat(mov.getNetoPagar()).isCloseTo(3000.00, within(0.01));
        assertThat(mov.getObservacion()).isEqualTo("Tiempo Efectivo: 3m 0d");
    }

    // ── Borde: ingreso posterior al fin del semestre → sin CTS este semestre (fallido) ──
    @Test
    void ingreso_posterior_al_semestre_no_genera_cts() {
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                vinculo(REGIMEN_276, 12000.0, LocalDate.of(2026, 5, 10)))); // después del 30-Abr
        when(motor.resolverRegimenLaboralCodigo(REGIMEN_276)).thenReturn("276");
        when(motor.resolverBaseRemunerativa(any(), eq("2026-05"))).thenReturn(new BigDecimal("12000"));

        CtsRegularResultDto r = service.generarCts("2026-05", null);

        assertThat(r.exitosos()).isZero();
        assertThat(r.fallidos()).hasSize(1);
    }

    // ── Error normativo: CAS/1057 no tiene CTS (regresión del comportamiento existente) ──
    @Test
    void cas_1057_no_genera_cts() {
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                vinculo(REGIMEN_CAS, 4500.0, LocalDate.of(2020, 1, 1))));
        when(motor.resolverRegimenLaboralCodigo(REGIMEN_CAS)).thenReturn("1057");

        CtsRegularResultDto r = service.generarCts("2026-05", null);

        assertThat(r.total()).isZero();
        assertThat(r.exitosos()).isZero();
        org.mockito.Mockito.verify(movimientoRepository, org.mockito.Mockito.never()).save(any());
    }

    // ── Guard: solo mayo/noviembre ──
    @Test
    void periodo_fuera_de_mayo_noviembre_lanza_excepcion() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.indeci.exception.NegocioException.class,
                () -> service.generarCts("2026-03", null));
    }
}
