package com.indeci.rrhh.service.cts;

import com.indeci.exception.CtsNoAplicableException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.cts.CtsLiquidacionResponseDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.LiquidacionCts;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.LiquidacionCtsRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.service.CalculoSnapshotService;
import com.indeci.rrhh.service.ParametroRemunerativoService;
import com.indeci.rrhh.service.cts.strategy.Cts276Strategy;
import com.indeci.rrhh.service.cts.strategy.CtsServirStrategy;
import com.indeci.rrhh.service.cts.strategy.CtsStrategyFactory;
import com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature 016 / US1 — pruebas del núcleo de cálculo CTS trunca (REGLA-07).
 * ParametroRemunerativoService está mockeado → los factores son parametrizados
 * (nada hardcodeado). No toca Oracle (H2/mocks).
 */
class CtsCalculadorServiceTest {

    private static final Long EMPLEADO_ID = 10L;
    private static final Long VINCULO_ID = 55L;
    private static final String PERIODO = "2026-06";

    private EmpleadoPlanillaRepository planillaRepository;
    private RegimenLaboralRepository regimenLaboralRepository;
    private LiquidacionCtsRepository liquidacionRepository;
    private ParametroRemunerativoService parametroService;
    private CalculoSnapshotService snapshotService;
    private IncidenciaLaboralCompuesta incidenciaLaboralCompuesta;
    private CtsCalculadorService service;

    @BeforeEach
    void setUp() {
        planillaRepository = mock(EmpleadoPlanillaRepository.class);
        regimenLaboralRepository = mock(RegimenLaboralRepository.class);
        liquidacionRepository = mock(LiquidacionCtsRepository.class);
        parametroService = mock(ParametroRemunerativoService.class);
        snapshotService = mock(CalculoSnapshotService.class);
        incidenciaLaboralCompuesta = mock(IncidenciaLaboralCompuesta.class);
        // Por defecto sin incidencias (cero regresión en los tests existentes).
        when(incidenciaLaboralCompuesta.calcularDesglose(any(), any(), any()))
                .thenReturn(com.indeci.rrhh.dto.DiasNoComputablesDto.cero());

        CtsStrategyFactory factory = new CtsStrategyFactory(List.of(
                new CtsServirStrategy(parametroService),
                new Cts276Strategy(parametroService)));

        service = new CtsCalculadorService(
                planillaRepository, regimenLaboralRepository, liquidacionRepository,
                parametroService, snapshotService, factory,
                new CtsCasGuard(), new CtsTiempoServiciosCalculator(), incidenciaLaboralCompuesta);

        when(liquidacionRepository.findByEmpleadoPlanillaIdAndPeriodo(VINCULO_ID, PERIODO))
                .thenReturn(Optional.empty());
        when(liquidacionRepository.save(any(LiquidacionCts.class))).thenAnswer(inv -> {
            LiquidacionCts l = inv.getArgument(0);
            if (l.getId() == null) l.setId(1L);
            return l;
        });
    }

    private EmpleadoPlanilla vinculo(Long regimenId, Double sueldo, LocalDate ingreso, LocalDate cese) {
        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setId(VINCULO_ID);
        v.setEmpleadoId(EMPLEADO_ID);
        v.setRegimenLaboralId(regimenId);
        v.setSueldoBasico(sueldo);
        v.setFechaInicioContrato(ingreso);
        v.setFechaCese(cese);
        return v;
    }

    private void stubRegimen(Long id, String codigo) {
        RegimenLaboral r = new RegimenLaboral();
        r.setCodigo(codigo);
        when(regimenLaboralRepository.findById(id)).thenReturn(Optional.of(r));
    }

    @Test
    void servir_vp_pura_calcula_total_exacto() {
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(3L, 5400.0, LocalDate.of(2022, 1, 1), LocalDate.of(2026, 6, 15))));
        stubRegimen(3L, "SERVIR");
        when(parametroService.obtenerValor(eq("CTS_FACTOR_ANUAL_SERVIR"), eq(2026), isNull()))
                .thenReturn(BigDecimal.ONE);
        when(parametroService.obtenerValor(eq("CTS_DIVISOR_DIAS"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("360"));

        CtsLiquidacionResponseDto r = service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO);

        assertEquals("CTSSERVIR", r.estrategia());
        assertEquals(4, r.anios());
        assertEquals(0, new BigDecimal("21600.00").compareTo(r.montoAnios()));
        assertEquals(0, new BigDecimal("2475.00").compareTo(r.montoFraccion()));
        assertEquals(0, new BigDecimal("24075.00").compareTo(r.montoTotal()));
        assertEquals("CALCULADO", r.estado());
    }

    @Test
    void servir_menos_de_un_anio_solo_fraccion() {
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(3L, 5400.0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15))));
        stubRegimen(3L, "SERVIR");
        when(parametroService.obtenerValor(eq("CTS_FACTOR_ANUAL_SERVIR"), eq(2026), isNull()))
                .thenReturn(BigDecimal.ONE);
        when(parametroService.obtenerValor(eq("CTS_DIVISOR_DIAS"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("360"));

        CtsLiquidacionResponseDto r = service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO);

        assertEquals(0, r.anios());
        assertEquals(0, new BigDecimal("0.00").compareTo(r.montoAnios()));
        assertEquals(0, new BigDecimal("2475.00").compareTo(r.montoTotal()));
    }

    @Test
    void doscientos76_usa_factor_parametrizado() {
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(1L, 3600.0, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 6, 15))));
        stubRegimen(1L, "276");
        // Factor 0.5 desde BD (REGLA-02): el total debe reflejarlo, no un 100% literal.
        when(parametroService.obtenerValor(eq("CTS_FACTOR_ANUAL_276"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("0.5"));
        when(parametroService.obtenerValor(eq("CTS_DIVISOR_DIAS"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("360"));

        CtsLiquidacionResponseDto r = service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO);

        assertEquals("CTS276", r.estrategia());
        assertEquals(6, r.anios());
        // 6 * 3600 * 0.5 = 10800.00
        assertEquals(0, new BigDecimal("10800.00").compareTo(r.montoAnios()));
    }

    @Test
    void cas_1057_bloqueado_sin_persistir() {
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(9L, 4500.0, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 6, 15))));
        stubRegimen(9L, "1057");

        CtsNoAplicableException ex = assertThrows(CtsNoAplicableException.class,
                () -> service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO));
        assertEquals("El régimen laboral CAS 1057 no contempla el beneficio de CTS según normativa vigente",
                ex.getMessage());
        verify(liquidacionRepository, never()).save(any());
    }

    @Test
    void fecha_cese_null_bloquea() {
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(3L, 5400.0, LocalDate.of(2022, 1, 1), null)));
        stubRegimen(3L, "SERVIR");

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO));
        assertEquals(true, ex.getMessage().contains("fecha de cese oficial"));
        verify(liquidacionRepository, never()).save(any());
    }

    @Test
    void base_cero_bloquea_sin_total_silencioso() {
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(3L, 0.0, LocalDate.of(2022, 1, 1), LocalDate.of(2026, 6, 15))));
        stubRegimen(3L, "SERVIR");

        assertThrows(NegocioException.class,
                () -> service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO));
        verify(liquidacionRepository, never()).save(any());
    }

    @Test
    void reingreso_liquida_el_vinculo_exacto_no_el_historico() {
        // Solo se resuelve el vínculo B por su empleadoPlanillaId (55); el histórico
        // A (cese 2024) nunca se consulta → tiempo/base salen del vínculo actual.
        EmpleadoPlanilla vinculoB =
                vinculo(3L, 5400.0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15));
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(vinculoB));
        stubRegimen(3L, "SERVIR");
        when(parametroService.obtenerValor(eq("CTS_FACTOR_ANUAL_SERVIR"), eq(2026), isNull()))
                .thenReturn(BigDecimal.ONE);
        when(parametroService.obtenerValor(eq("CTS_DIVISOR_DIAS"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("360"));

        CtsLiquidacionResponseDto r = service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO);

        assertEquals(LocalDate.of(2026, 1, 1), r.fechaIngreso()); // del vínculo B, no de A
        assertEquals(0, r.anios());
    }

    @Test
    void lsg_y_faltas_reducen_el_tiempo_de_servicios_y_el_monto() {
        // Igual que servir_vp_pura_calcula_total_exacto (4 años, 5m15d = 165 fracción),
        // pero con 40 días de LSG + faltas → fracción neta 165-40=125 → 4 años, 4m 5d.
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(3L, 5400.0, LocalDate.of(2022, 1, 1), LocalDate.of(2026, 6, 15))));
        stubRegimen(3L, "SERVIR");
        when(parametroService.obtenerValor(eq("CTS_FACTOR_ANUAL_SERVIR"), eq(2026), isNull()))
                .thenReturn(BigDecimal.ONE);
        when(parametroService.obtenerValor(eq("CTS_DIVISOR_DIAS"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("360"));
        when(incidenciaLaboralCompuesta.calcularDesglose(eq(EMPLEADO_ID), any(), any()))
                .thenReturn(com.indeci.rrhh.dto.DiasNoComputablesDto.of(30, 10));

        CtsLiquidacionResponseDto r = service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO);

        assertEquals(4, r.anios());
        // montoAnios sin cambio (4 años completos): 4 * 5400 * 1 = 21600.00
        assertEquals(0, new BigDecimal("21600.00").compareTo(r.montoAnios()));
        // fracción neta 125 días: (5400*1/360) * 125 = 1875.00 (antes 2475.00 sin descuento)
        assertEquals(0, new BigDecimal("1875.00").compareTo(r.montoFraccion()));
        assertEquals(0, new BigDecimal("23475.00").compareTo(r.montoTotal()));
    }

    @Test
    void sin_incidencias_el_monto_es_identico_al_caso_base() {
        // Cero regresión explícita: con DiasNoComputablesDto.cero() (default del setUp),
        // el resultado debe ser idéntico al caso servir_vp_pura_calcula_total_exacto.
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(3L, 5400.0, LocalDate.of(2022, 1, 1), LocalDate.of(2026, 6, 15))));
        stubRegimen(3L, "SERVIR");
        when(parametroService.obtenerValor(eq("CTS_FACTOR_ANUAL_SERVIR"), eq(2026), isNull()))
                .thenReturn(BigDecimal.ONE);
        when(parametroService.obtenerValor(eq("CTS_DIVISOR_DIAS"), eq(2026), isNull()))
                .thenReturn(new BigDecimal("360"));

        CtsLiquidacionResponseDto r = service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO);

        assertEquals(0, new BigDecimal("24075.00").compareTo(r.montoTotal()));
    }

    @Test
    void liquidacion_cerrada_es_inmutable() {
        when(planillaRepository.findById(VINCULO_ID)).thenReturn(Optional.of(
                vinculo(3L, 5400.0, LocalDate.of(2022, 1, 1), LocalDate.of(2026, 6, 15))));
        stubRegimen(3L, "SERVIR");
        LiquidacionCts cerrada = new LiquidacionCts();
        cerrada.setEstado("CERRADO");
        when(liquidacionRepository.findByEmpleadoPlanillaIdAndPeriodo(VINCULO_ID, PERIODO))
                .thenReturn(Optional.of(cerrada));

        assertThrows(NegocioException.class,
                () -> service.calcular(EMPLEADO_ID, VINCULO_ID, PERIODO));
        verify(liquidacionRepository, never()).save(any());
    }
}
