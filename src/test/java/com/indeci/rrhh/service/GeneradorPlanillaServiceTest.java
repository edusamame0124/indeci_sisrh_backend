package com.indeci.rrhh.service;

import com.indeci.exception.ConceptoSinCodigoMefException;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 010 — Tests del motor refactor.
 *
 * Desde la Corrección A: el motor toma los ingresos remunerativos
 * EXCLUSIVAMENTE de EmpleadoConcepto (no de EmpleadoPlanilla.sueldoBasico).
 * Por eso cada caso configura el sueldo como un EmpleadoConcepto remunerativo
 * (helper {@code ecSueldo}). El motor además IGNORA los EmpleadoConcepto cuyo
 * CODIGO_MEF él calcula solo (aportes, ESSALUD, 5ta, asig. familiar).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeneradorPlanillaServiceTest {

    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private MovimientoPlanillaDetalleRepository detalleRepository;
    @Mock private PeriodoPlanillaRepository periodoRepository;
    @Mock private EmpleadoConceptoRepository empleadoConceptoRepository;
    @Mock private AsistenciaCabeceraRepository asistenciaCabeceraRepository;
    @Mock private EmpleadoPensionRepository empleadoPensionRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private RegimenPensionarioRepository regimenPensionarioRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private ParametroRemunerativoService parametroService;
    @Mock private ConciliacionAirhspRepository conciliacionRepository;
    @Mock private AbonoBancoRepository abonoBancoRepository;

    private GeneradorPlanillaService service;

    private static final Long EMPLEADO_ID = 41L;
    private static final String PERIODO   = "2026-05";
    private static final Long REG_276     = 1L;
    private static final Long REG_728     = 2L;
    private static final Long REG_PENS_ONP_ID = 10L;
    private static final Long REG_PENS_AFP_ID = 11L;

    /** Concepto remunerativo de sueldo básico (afecto a pensión y ESSALUD). */
    private static final Long CONCEPTO_SUELDO_ID = 90000L;

    @BeforeEach
    void setUp() {
        service = new GeneradorPlanillaService(
                planillaRepository,
                conceptoRepository,
                movimientoRepository,
                detalleRepository,
                periodoRepository,
                empleadoConceptoRepository,
                asistenciaCabeceraRepository,
                empleadoPensionRepository,
                empleadoRepository,
                regimenPensionarioRepository,
                regimenLaboralRepository,
                parametroService,
                conciliacionRepository,
                abonoBancoRepository);
        // El proxy @Lazy `self` no se inyecta en test unitario: apuntarlo al
        // propio servicio para poder ejercitar generarTodoPeriodo.
        ReflectionTestUtils.setField(service, "self", service);

        // Periodo abierto por defecto
        PeriodoPlanilla periodo = new PeriodoPlanilla();
        periodo.setPeriodo(PERIODO);
        periodo.setEstado("ABIERTO");
        periodo.setActivo(1);
        when(periodoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(Optional.of(periodo));

        // No hay movimiento anterior
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());

        // save retorna el mismo objeto con id=100 (cabecera)
        when(movimientoRepository.save(any(MovimientoPlanilla.class)))
                .thenAnswer(inv -> {
                    MovimientoPlanilla m = inv.getArgument(0);
                    if (m.getId() == null) m.setId(100L);
                    return m;
                });
        when(detalleRepository.save(any(MovimientoPlanillaDetalle.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Regímenes laborales y pensionarios
        when(regimenLaboralRepository.findById(REG_276))
                .thenReturn(Optional.of(regimenLaboral("276")));
        when(regimenLaboralRepository.findById(REG_728))
                .thenReturn(Optional.of(regimenLaboral("728")));
        when(regimenPensionarioRepository.findById(REG_PENS_ONP_ID))
                .thenReturn(Optional.of(regimenPensionario("ONP")));
        when(regimenPensionarioRepository.findById(REG_PENS_AFP_ID))
                .thenReturn(Optional.of(regimenPensionario("AFP")));

        // Parámetros 2026
        when(parametroService.obtenerValor(eq("TASA_ONP"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.13"));
        when(parametroService.obtenerValor(eq("TASA_AFP_APORTE"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.10"));
        when(parametroService.obtenerValor(eq("TASA_ESSALUD"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.09"));
        when(parametroService.obtenerValor(eq("ASIG_FAMILIAR"), anyInt(), any()))
                .thenReturn(new BigDecimal("102.50"));
        when(parametroService.obtenerValor(eq("ESSALUD_MINIMO"), anyInt(), any()))
                .thenReturn(new BigDecimal("101.70"));
        when(parametroService.obtenerValor(eq("TASA_ESSALUD_EPS_EMPLEADOR"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.0675"));
        when(parametroService.obtenerValor(eq("TASA_ESSALUD_EPS_COPAGO"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.0225"));
        when(parametroService.obtenerValor(eq("TOPE_SEGURO_AFP"), anyInt(), any()))
                .thenReturn(new BigDecimal("12209.11"));
        when(parametroService.obtenerValor(eq("PRIMA_AFP"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.0137"));
        // 5ta categoría — UIT + escala progresiva (§16.2)
        when(parametroService.obtenerValor(eq("UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("5350"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO1_LIM_UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("5"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO2_LIM_UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("20"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO3_LIM_UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("35"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO4_LIM_UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("45"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO1_TASA"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.08"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO2_TASA"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.14"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO3_TASA"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.17"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO4_TASA"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.20"));
        when(parametroService.obtenerValor(eq("IR5TA_TRAMO5_TASA"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.30"));

        // Conceptos MEF
        when(conceptoRepository.findByCodigoMefAndActivo("05001", 1))
                .thenReturn(Optional.of(conceptoMef(501L, "05001", "Aporte ONP 13%", "APORTE_TRABAJADOR")));
        when(conceptoRepository.findByCodigoMefAndActivo("05002", 1))
                .thenReturn(Optional.of(conceptoMef(502L, "05002", "Aporte AFP 10%", "APORTE_TRABAJADOR")));
        when(conceptoRepository.findByCodigoMefAndActivo("05003", 1))
                .thenReturn(Optional.of(conceptoMef(503L, "05003", "Comisión AFP", "APORTE_TRABAJADOR")));
        when(conceptoRepository.findByCodigoMefAndActivo("05004", 1))
                .thenReturn(Optional.of(conceptoMef(504L, "05004", "Prima Seguro AFP", "APORTE_TRABAJADOR")));
        when(conceptoRepository.findByCodigoMefAndActivo("06001", 1))
                .thenReturn(Optional.of(conceptoMef(601L, "06001", "ESSALUD 9%", "APORTE_EMPLEADOR")));
        when(conceptoRepository.findByCodigoMefAndActivo("06002", 1))
                .thenReturn(Optional.of(conceptoMef(602L, "06002", "ESSALUD 6.75% con EPS", "APORTE_EMPLEADOR")));
        when(conceptoRepository.findByCodigoMefAndActivo("05309", 1))
                .thenReturn(Optional.of(conceptoMef(309L, "05309", "Copago EPS", "DESCUENTO")));
        when(conceptoRepository.findByCodigoMefAndActivo("00302", 1))
                .thenReturn(Optional.of(asigFamiliar728()));
        when(conceptoRepository.findByCodigoMefAndActivo("05101", 1))
                .thenReturn(Optional.of(conceptoMef(5101L, "05101", "Retención IR 5ta Categoría", "DESCUENTO")));
        when(conceptoRepository.findByCodigoMefAndActivo("05401", 1))
                .thenReturn(Optional.of(conceptoMef(5401L, "05401", "Descuento por Tardanza", "DESCUENTO")));
        when(conceptoRepository.findByCodigoMefAndActivo("05402", 1))
                .thenReturn(Optional.of(conceptoMef(5402L, "05402", "Descuento por Falta", "DESCUENTO")));

        // Sin asistencia por defecto (los tests de PASO 7 lo sobrescriben)
        when(asistenciaCabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());

        // Concepto de sueldo básico (lo referencian los EmpleadoConcepto de sueldo)
        when(conceptoRepository.findById(CONCEPTO_SUELDO_ID))
                .thenReturn(Optional.of(conceptoSueldo()));

        // Sin conceptos manuales por defecto (cada test inyecta el sueldo)
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of());

        // Empleado sin EPS por defecto (los tests de EPS lo sobrescriben)
        when(empleadoRepository.findById(EMPLEADO_ID))
                .thenReturn(Optional.empty());
    }

    // ==================================================================
    // CASO 1: Régimen 276 + ONP feliz
    // ==================================================================
    @Test
    void caso_276_ONP_calcula_aporte_13pct_y_essalud_empleador_9pct() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, /*asigFam=*/0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        mockSueldo(3000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + ONP + ESSALUD = 3
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(3)).save(capt.capture());

        assertThat(detallePorConcepto(capt, 501L).getMonto())   // ONP = 3000×0.13
                .isCloseTo(390.00, within(0.01));
        assertThat(detallePorConcepto(capt, 601L).getMonto())   // ESSALUD = 3000×0.09
                .isCloseTo(270.00, within(0.01));

        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalIngresos()).isCloseTo(3000.00, within(0.01));
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(390.00, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(2610.00, within(0.01));
    }

    // ==================================================================
    // CASO 2: Régimen 728 + AFP con asignación familiar
    // ==================================================================
    @Test
    void caso_728_AFP_con_asig_familiar_aplica_aporte_comision_y_seguro() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(4000.0, REG_728, /*asigFam=*/1)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_AFP_ID, null, 0.016, 0.0174)));
        mockSueldo(4000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + asig.fam + aporte + comisión + seguro + 5ta + ESSALUD = 7
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(7)).save(capt.capture());

        assertThat(detallePorConcepto(capt, 302L).getMonto())   // asig.fam
                .isCloseTo(102.50, within(0.01));
        assertThat(detallePorConcepto(capt, 502L).getMonto())   // aporte AFP 10% sobre 4102.50
                .isCloseTo(410.25, within(0.01));
        assertThat(detallePorConcepto(capt, 503L).getMonto())   // comisión 1.6%
                .isCloseTo(65.64, within(0.01));
        assertThat(detallePorConcepto(capt, 504L).getMonto())   // prima 1.74%
                .isCloseTo(71.38, within(0.01));
        assertThat(detallePorConcepto(capt, 601L).getMonto())   // ESSALUD 9% sobre 4000
                .isCloseTo(360.00, within(0.01));
        // 5ta: 4102.50×12=49230 ; −37450 = 11780 ; ×0.08 = 942.40 ; /12 = 78.53
        assertThat(detallePorConcepto(capt, 5101L).getMonto())
                .isCloseTo(78.53, within(0.01));

        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalIngresos()).isCloseTo(4102.50, within(0.01));
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(625.80, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(4102.50 - 625.80, within(0.01));
    }

    // ==================================================================
    // CASO 3: LEY-01 — concepto manual sin CODIGO_MEF
    // ==================================================================
    @Test
    void caso_concepto_manual_sin_codigo_mef_lanza_excepcion() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());

        ConceptoPlanilla legacy = new ConceptoPlanilla();
        legacy.setId(999L);
        legacy.setCodigo("BONO-EXTRA");
        legacy.setNombre("Bono extraordinario");
        legacy.setTipoConcepto("REMUNERATIVO");
        legacy.setCodigoMef(null); // ← LEY-01: prohibido
        legacy.setActivo(1);
        when(conceptoRepository.findById(999L)).thenReturn(Optional.of(legacy));

        EmpleadoConcepto ec = new EmpleadoConcepto();
        ec.setEmpleadoId(EMPLEADO_ID);
        ec.setConceptoPlanillaId(999L);
        ec.setMonto(500.0);
        ec.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(ec));

        assertThatThrownBy(() -> service.generar(EMPLEADO_ID, PERIODO))
                .isInstanceOf(ConceptoSinCodigoMefException.class)
                .hasMessageContaining("Ley 32448");
    }

    // ==================================================================
    // CASO 4 (3a): ESSALUD con mínimo regulatorio (§5.5)
    // ==================================================================
    @Test
    void caso_essalud_aplica_minimo_cuando_base_es_baja() {
        // sueldo 1000 → 1000 × 0.09 = 90 < 101.70 → essaludBase = mínimo 101.70
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(1000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(1000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + ESSALUD = 2
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(2)).save(capt.capture());

        assertThat(detallePorConcepto(capt, 601L).getMonto())   // MAX(90, 101.70)
                .isCloseTo(101.70, within(0.01));
    }

    // ==================================================================
    // CASO 5 (3a): ESSALUD con EPS — split 6.75% empleador / 2.25% copago
    // ==================================================================
    @Test
    void caso_essalud_con_eps_divide_675_empleador_y_225_copago() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(4000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        when(empleadoRepository.findById(EMPLEADO_ID))
                .thenReturn(Optional.of(empleadoConEps()));
        mockSueldo(4000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + ESSALUD_EPS(602) + copago(309) = 3
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(3)).save(capt.capture());

        // essaludBase = MAX(4000×0.09, 101.70) = 360
        assertThat(detallePorConcepto(capt, 602L).getMonto())   // 360 × 0.75
                .isCloseTo(270.00, within(0.01));
        assertThat(detallePorConcepto(capt, 309L).getMonto())   // 360 × 0.25
                .isCloseTo(90.00, within(0.01));

        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalIngresos()).isCloseTo(4000.00, within(0.01));
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(90.00, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(3910.00, within(0.01));
    }

    // ==================================================================
    // CASO 6 (3b): prima AFP — base SUPERA el tope → se recorta (§5.6)
    // ==================================================================
    @Test
    void caso_prima_afp_aplica_tope_cuando_base_supera_tope() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(15000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_AFP_ID, null, null, null)));
        mockSueldo(15000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + aporte AFP(502) + prima(504) + ESSALUD(601) = 4
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(4)).save(capt.capture());

        assertThat(detallePorConcepto(capt, 502L).getMonto())   // 15000 × 0.10
                .isCloseTo(1500.00, within(0.01));
        // prima: base topada = MIN(15000, 12209.11) = 12209.11 × 0.0137 = 167.26
        assertThat(detallePorConcepto(capt, 504L).getMonto())
                .isCloseTo(167.26, within(0.01));

        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(1667.26, within(0.01));
    }

    // ==================================================================
    // CASO 7 (3b): prima AFP — base BAJO el tope → sin recorte
    // ==================================================================
    @Test
    void caso_prima_afp_sin_recorte_cuando_base_bajo_tope() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(5000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_AFP_ID, null, null, null)));
        mockSueldo(5000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + aporte(502) + prima(504) + ESSALUD(601) = 4
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(4)).save(capt.capture());

        assertThat(detallePorConcepto(capt, 504L).getMonto())   // 5000 × 0.0137
                .isCloseTo(68.50, within(0.01));
    }

    // ==================================================================
    // CASO 8 (3c): 728 — renta supera 7 UIT → retiene 5ta (un tramo)
    // ==================================================================
    @Test
    void caso_5ta_728_renta_supera_7uit_retiene() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(5000.0, REG_728, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(5000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + 5ta(5101) + ESSALUD(601) = 3
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(3)).save(capt.capture());

        // rentaAnual = 5000×12 = 60000 ; rentaNeta = 22550 ; ×0.08 = 1804 ; /12 = 150.33
        assertThat(detallePorConcepto(capt, 5101L).getMonto())
                .isCloseTo(150.33, within(0.01));

        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(150.33, within(0.01));
    }

    // ==================================================================
    // CASO 9 (3c): 728 — renta bajo 7 UIT → NO retiene (caso borde)
    // ==================================================================
    @Test
    void caso_5ta_728_renta_bajo_7uit_no_retiene() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(2000.0, REG_728, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(2000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + ESSALUD — la 5ta no se graba
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(2)).save(capt.capture());
        assertThat(capt.getAllValues())
                .noneMatch(d -> Long.valueOf(5101L).equals(d.getConceptoPlanillaId()));
    }

    // ==================================================================
    // CASO 10 (3c): régimen 276 — NUNCA retiene 5ta (LEY-03)
    // ==================================================================
    @Test
    void caso_5ta_regimen_276_nunca_retiene() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(10000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(10000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + ESSALUD — sin 5ta
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(2)).save(capt.capture());
        assertThat(capt.getAllValues())
                .noneMatch(d -> Long.valueOf(5101L).equals(d.getConceptoPlanillaId()));
    }

    // ==================================================================
    // CASO 11 (3c): escala progresiva 5ta — renta alta atraviesa 3 tramos
    // ==================================================================
    @Test
    void caso_5ta_escala_progresiva_multitramo() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(15000.0, REG_728, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(15000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + 5ta(5101) + ESSALUD(601) = 3
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(3)).save(capt.capture());

        // rentaNeta = 180000 − 37450 = 142550
        // tramo1 26750×0.08=2140 ; tramo2 80250×0.14=11235 ; tramo3 35550×0.17=6043.50
        // impuestoAnual = 19418.50 ; BW = 19418.50/12 = 1618.21
        assertThat(detallePorConcepto(capt, 5101L).getMonto())
                .isCloseTo(1618.21, within(0.01));
    }

    // ==================================================================
    // CASO 12 (3d): ESTADO_NETO = BIEN cuando el neto supera el umbral 50%
    // ==================================================================
    @Test
    void caso_estado_neto_bien_cuando_neto_supera_umbral() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        mockSueldo(3000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // umbral = (3000 − 0 ir5ta − 390 ONP − 0 judicial) × 0.5 = 1305
        // neto   = 3000 − 390 = 2610 ≥ 1305 → BIEN
        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getNeto50pctMinimo()).isCloseTo(1305.00, within(0.01));
        assertThat(cabecera.getEstadoNeto()).isEqualTo("BIEN");
    }

    // ==================================================================
    // CASO 13 (3d): ESTADO_NETO = NETO_NO_VA — descuento voluntario hunde
    // el neto bajo el umbral 50% (REGLA SERVIR-07)
    // ==================================================================
    @Test
    void caso_estado_neto_no_va_cuando_descuento_voluntario_excede_umbral() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));

        // Sueldo + descuento voluntario 1400 (05310 no es auto-calculado).
        ConceptoPlanilla descVol = conceptoMef(700L, "05310", "Otros Descuentos", "DESCUENTO");
        when(conceptoRepository.findById(700L)).thenReturn(Optional.of(descVol));
        EmpleadoConcepto ecDesc = new EmpleadoConcepto();
        ecDesc.setEmpleadoId(EMPLEADO_ID);
        ecDesc.setConceptoPlanillaId(700L);
        ecDesc.setMonto(1400.0);
        ecDesc.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(ecSueldo(3000.0), ecDesc));

        service.generar(EMPLEADO_ID, PERIODO);

        // umbral = (3000 − 0 − 390 − 0) × 0.5 = 1305
        // neto   = 3000 − (390 ONP + 1400 voluntario) = 1210 < 1305 → NETO_NO_VA
        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getNeto50pctMinimo()).isCloseTo(1305.00, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(1210.00, within(0.01));
        assertThat(cabecera.getEstadoNeto()).isEqualTo("NETO_NO_VA");
        assertThat(cabecera.getEstado()).isEqualTo("REVISAR");
    }

    // ==================================================================
    // CASO 14 (Corrección A): el motor ignora EmpleadoConcepto cuyo
    // CODIGO_MEF él calcula solo — no hay doble conteo.
    // ==================================================================
    @Test
    void caso_concepto_manual_auto_calculado_se_ignora_sin_duplicar() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));

        // Además del sueldo, un EmpleadoConcepto legacy de "Aporte ONP" (05001):
        // el motor lo calcula automáticamente → este manual debe IGNORARSE.
        ConceptoPlanilla aporteLegacy =
                conceptoMef(8001L, "05001", "Aporte ONP 13%", "APORTE_TRABAJADOR");
        when(conceptoRepository.findById(8001L)).thenReturn(Optional.of(aporteLegacy));
        EmpleadoConcepto ecLegacy = new EmpleadoConcepto();
        ecLegacy.setEmpleadoId(EMPLEADO_ID);
        ecLegacy.setConceptoPlanillaId(8001L);
        ecLegacy.setMonto(999.0);          // monto basura — no debe contar
        ecLegacy.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(ecSueldo(3000.0), ecLegacy));

        service.generar(EMPLEADO_ID, PERIODO);

        // El aporte ONP debe ser el calculado por el motor (390), NO 999.
        // Descuentos = solo aporte ONP 390 (el manual 05001 se ignoró).
        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalIngresos()).isCloseTo(3000.00, within(0.01));
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(390.00, within(0.01));
    }

    // ==================================================================
    // CASO 15 (PASO 7): asistencia VALIDADA aplica descuento tardanza + falta
    // ==================================================================
    @Test
    void caso_asistencia_validada_aplica_descuento_tardanza_y_falta() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        mockSueldo(3000.0);

        // Asistencia VALIDADA: 25.00 tardanza + 100.00 falta.
        when(asistenciaCabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(asistenciaCabecera("VALIDADA", 25.00, 100.00)));

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + tardanza(5401) + falta(5402) + ONP(501) + ESSALUD(601) = 5
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(5)).save(capt.capture());

        assertThat(detallePorConcepto(capt, 5401L).getMonto()).isCloseTo(25.00, within(0.01));
        assertThat(detallePorConcepto(capt, 5402L).getMonto()).isCloseTo(100.00, within(0.01));

        // descuentos = 390 ONP + 25 tardanza + 100 falta = 515
        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalIngresos()).isCloseTo(3000.00, within(0.01));
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(515.00, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(2485.00, within(0.01));
    }

    // ==================================================================
    // CASO 16 (PASO 7): asistencia en BORRADOR NO alimenta el motor (borde)
    // ==================================================================
    @Test
    void caso_asistencia_borrador_no_aplica_descuento() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        mockSueldo(3000.0);

        // Asistencia en BORRADOR con descuentos cargados — debe IGNORARSE.
        when(asistenciaCabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(asistenciaCabecera("BORRADOR", 25.00, 100.00)));

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + ONP(501) + ESSALUD(601) = 3 — sin tardanza/falta
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(3)).save(capt.capture());
        assertThat(capt.getAllValues())
                .noneMatch(d -> Long.valueOf(5401L).equals(d.getConceptoPlanillaId())
                        || Long.valueOf(5402L).equals(d.getConceptoPlanillaId()));

        // descuentos = solo 390 ONP
        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(390.00, within(0.01));
    }

    // ==================================================================
    // CASO 17 (PASO 16): conciliación AIRHSP — montos iguales → CONCILIADO
    // ==================================================================
    @Test
    void caso_paso16_conciliacion_airhsp_cuadra_nace_conciliado() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        when(empleadoRepository.findById(EMPLEADO_ID))
                .thenReturn(Optional.of(empleadoConAirhsp(3000.0)));
        mockSueldo(3000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        ArgumentCaptor<ConciliacionAirhsp> capt =
                ArgumentCaptor.forClass(ConciliacionAirhsp.class);
        verify(conciliacionRepository).save(capt.capture());
        ConciliacionAirhsp c = capt.getValue();
        assertThat(c.getMontoSistema()).isCloseTo(3000.00, within(0.01));
        assertThat(c.getMontoAirhsp()).isCloseTo(3000.00, within(0.01));
        assertThat(c.getEstado()).isEqualTo("CONCILIADO");
    }

    // ==================================================================
    // CASO 18 (PASO 16): conciliación AIRHSP — discrepancia → PENDIENTE
    // ==================================================================
    @Test
    void caso_paso16_conciliacion_airhsp_con_discrepancia_nace_pendiente() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        when(empleadoRepository.findById(EMPLEADO_ID))
                .thenReturn(Optional.of(empleadoConAirhsp(2500.0))); // AIRHSP ≠ sistema
        mockSueldo(3000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        ArgumentCaptor<ConciliacionAirhsp> capt =
                ArgumentCaptor.forClass(ConciliacionAirhsp.class);
        verify(conciliacionRepository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("PENDIENTE");
    }

    // ==================================================================
    // CASO 19 (C2 / BKD-001): generación masiva — todos fallan, no aborta
    // ==================================================================
    @Test
    void generarTodoPeriodo_reporta_total_exitosos_y_fallidos() {
        EmpleadoPlanilla p1 = new EmpleadoPlanilla();
        p1.setEmpleadoId(41L);
        EmpleadoPlanilla p2 = new EmpleadoPlanilla();
        p2.setEmpleadoId(99L);
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(p1, p2));
        // Sin findFirstByEmpleadoIdAndActivo → ambos generan "sin configuración".

        var r = service.generarTodoPeriodo(PERIODO);

        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getExitosos()).isZero();
        assertThat(r.getFallidos()).hasSize(2);
        assertThat(r.getFallidos().get(0).getEmpleadoId()).isEqualTo(41L);
        assertThat(r.getFallidos().get(0).getRazon()).isNotBlank();
    }

    // ==================================================================
    // CASO 20 (C2 / BKD-001): generación masiva mixta — 1 OK + 1 falla
    // ==================================================================
    @Test
    void generarTodoPeriodo_mixto_cuenta_un_exitoso_y_un_fallido() {
        EmpleadoPlanilla p1 = planilla(3000.0, REG_276, 0); // emp 41 — completo
        EmpleadoPlanilla p2 = new EmpleadoPlanilla();
        p2.setEmpleadoId(99L);                              // emp 99 — sin config
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(p1, p2));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(p1));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(3000.0);

        var r = service.generarTodoPeriodo(PERIODO);

        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getExitosos()).isEqualTo(1);
        assertThat(r.getFallidos()).hasSize(1);
        assertThat(r.getFallidos().get(0).getEmpleadoId()).isEqualTo(99L);
    }

    // ==================================================================
    // HELPERS
    // ==================================================================

    private AsistenciaCabecera asistenciaCabecera(
            String estado, double descTardanza, double descFalta) {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEmpleadoId(EMPLEADO_ID);
        cab.setPeriodo(PERIODO);
        cab.setEstado(estado);
        cab.setTotalMinTardanza(120);
        cab.setDiasFalta(1);
        cab.setDescuentoTardanza(descTardanza);
        cab.setDescuentoFalta(descFalta);
        cab.setActivo(1);
        return cab;
    }

    /** Configura el sueldo del empleado como un EmpleadoConcepto remunerativo. */
    private void mockSueldo(double monto) {
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(ecSueldo(monto)));
    }

    private EmpleadoConcepto ecSueldo(double monto) {
        EmpleadoConcepto ec = new EmpleadoConcepto();
        ec.setEmpleadoId(EMPLEADO_ID);
        ec.setConceptoPlanillaId(CONCEPTO_SUELDO_ID);
        ec.setMonto(monto);
        ec.setActivo(1);
        return ec;
    }

    /** Concepto remunerativo de sueldo básico — afecto a pensión y ESSALUD. */
    private ConceptoPlanilla conceptoSueldo() {
        ConceptoPlanilla c = conceptoMef(CONCEPTO_SUELDO_ID, "00301", "Sueldo Básico", "REMUNERATIVO");
        c.setAfectoAportePens("S");
        c.setAfectoEssalud("S");
        c.setAfectoIr5ta("S");
        return c;
    }

    private Empleado empleadoConEps() {
        Empleado e = new Empleado();
        e.setId(EMPLEADO_ID);
        e.setHasEps("S");
        return e;
    }

    private Empleado empleadoConAirhsp(double airhspMonto) {
        Empleado e = new Empleado();
        e.setId(EMPLEADO_ID);
        e.setAirhspMonto(airhspMonto);
        return e;
    }

    private EmpleadoPlanilla planilla(double sueldoBasico, Long regimenLaboralId, int asigFam) {
        EmpleadoPlanilla p = new EmpleadoPlanilla();
        p.setEmpleadoId(EMPLEADO_ID);
        p.setSueldoBasico(sueldoBasico);
        p.setRegimenLaboralId(regimenLaboralId);
        p.setTieneAsignacionFamiliar(asigFam);
        p.setNumHijos(asigFam == 1 ? 1 : 0);
        p.setActivo(1);
        return p;
    }

    private EmpleadoPension pension(Long regimenPensId, Double aporte, Double comision, Double seguro) {
        EmpleadoPension ep = new EmpleadoPension();
        ep.setEmpleadoId(EMPLEADO_ID);
        ep.setRegimenPensionarioId(regimenPensId);
        ep.setPorcentajeAporte(aporte);
        ep.setPorcentajeComision(comision);
        ep.setPorcentajeSeguro(seguro);
        ep.setActivo(1);
        return ep;
    }

    private RegimenLaboral regimenLaboral(String codigo) {
        RegimenLaboral r = new RegimenLaboral();
        r.setCodigo(codigo);
        r.setActivo(1);
        return r;
    }

    private RegimenPensionario regimenPensionario(String tipo) {
        RegimenPensionario r = new RegimenPensionario();
        // El motor resuelve el régimen por TIPO (ONP|AFP), no por CODIGO.
        r.setTipo(tipo);
        r.setActivo(1);
        return r;
    }

    private ConceptoPlanilla conceptoMef(Long id, String codigoMef, String nombre, String tipoConcepto) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(id);
        c.setCodigo(codigoMef);
        c.setCodigoMef(codigoMef);
        c.setNombre(nombre);
        c.setTipoConcepto(tipoConcepto);
        c.setAfectoAportePens("N");
        c.setAfectoEssalud("N");
        c.setAfectoIr5ta("N");
        c.setEsMuc("N");
        c.setEsCuc("N");
        c.setRegimenAplicable("TODOS");
        c.setActivo(1);
        return c;
    }

    private ConceptoPlanilla asigFamiliar728() {
        ConceptoPlanilla c = conceptoMef(302L, "00302", "Asignación Familiar", "REMUNERATIVO");
        c.setAfectoAportePens("S");
        c.setAfectoEssalud("N");
        c.setAfectoIr5ta("S");
        c.setRegimenAplicable("728");
        return c;
    }

    private MovimientoPlanillaDetalle detallePorConcepto(
            ArgumentCaptor<MovimientoPlanillaDetalle> capt, Long conceptoId) {
        return capt.getAllValues().stream()
                .filter(d -> conceptoId.equals(d.getConceptoPlanillaId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No se grabó detalle para concepto id=" + conceptoId
                                + ". Conceptos grabados: "
                                + capt.getAllValues().stream()
                                    .map(MovimientoPlanillaDetalle::getConceptoPlanillaId)
                                    .toList()));
    }

    private MovimientoPlanilla capturarCabeceraFinal() {
        ArgumentCaptor<MovimientoPlanilla> capt = ArgumentCaptor.forClass(MovimientoPlanilla.class);
        verify(movimientoRepository, times(2)).save(capt.capture());
        return capt.getAllValues().get(capt.getAllValues().size() - 1);
    }
}
