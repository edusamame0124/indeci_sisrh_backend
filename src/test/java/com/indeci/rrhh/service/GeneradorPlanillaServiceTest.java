package com.indeci.rrhh.service;

import com.indeci.exception.ConceptoSinCodigoMefException;
import com.indeci.rrhh.dto.Suspension4taVigenteDto;
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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
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
    @Mock private EmpleadoReintegroRepository empleadoReintegroRepository;
    @Mock private EmpleadoEventoRepository empleadoEventoRepository;
    @Mock private Suspension4taService suspension4taService;
    @Mock private TipoPersonalRepository tipoPersonalRepository;
    @Mock private CalculoSnapshotService calculoSnapshotService;
    @Mock private SubsidioCalculadorService subsidioCalculadorService;

    private GeneradorPlanillaService service;

    private static final Long EMPLEADO_ID = 41L;
    private static final String PERIODO   = "2026-05";
    private static final Long REG_276     = 1L;
    private static final Long REG_728     = 2L;
    private static final Long REG_CAS     = 3L;
    private static final Long REG_PENS_ONP_ID = 10L;
    private static final Long REG_PENS_AFP_ID = 11L;
    private static final Long REG_PENS_PENSIONISTA_ID = 12L;
    private static final Long REG_PENS_RETIRO_ID = 13L;
    private static final Long REG_PENS_SIN_REGIMEN_ID = 14L;

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
                abonoBancoRepository,
                empleadoReintegroRepository,
                empleadoEventoRepository,
                suspension4taService,
                tipoPersonalRepository,
                calculoSnapshotService,
                subsidioCalculadorService);
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
        when(regimenLaboralRepository.findById(REG_CAS))
                .thenReturn(Optional.of(regimenLaboral("CAS")));
        when(regimenPensionarioRepository.findById(REG_PENS_ONP_ID))
                .thenReturn(Optional.of(regimenPensionario("ONP")));
        when(regimenPensionarioRepository.findById(REG_PENS_AFP_ID))
                .thenReturn(Optional.of(regimenPensionario("AFP")));
        when(regimenPensionarioRepository.findById(REG_PENS_PENSIONISTA_ID))
                .thenReturn(Optional.of(regimenPensionario("PENSIONISTA")));
        when(regimenPensionarioRepository.findById(REG_PENS_RETIRO_ID))
                .thenReturn(Optional.of(regimenPensionario("RETIRO")));
        when(regimenPensionarioRepository.findById(REG_PENS_SIN_REGIMEN_ID))
                .thenReturn(Optional.of(regimenPensionario("SIN_REGIMEN")));

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
        // FASE 3 — divisor del Art. 40 para el período de prueba (mayo = 8).
        when(parametroService.obtenerValorOpcional(eq("IR5TA_DIVISOR_MES_05"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("8")));

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

        // Conceptos de remuneración BASE (mejora 2026-06-03): el motor graba la
        // base desde EmpleadoPlanilla.sueldoBasico con el concepto base del régimen.
        when(conceptoRepository.findByCodigoMefAndActivo("00301", 1))
                .thenReturn(Optional.of(conceptoBaseRem(10301L, "00301", "Sueldo Básico")));
        when(conceptoRepository.findByCodigoMefAndActivo("00501", 1))
                .thenReturn(Optional.of(conceptoBaseRem(10501L, "00501", "Remuneración CAS")));
        when(conceptoRepository.findByCodigoMefAndActivo("00102", 1))
                .thenReturn(Optional.of(conceptoBaseRem(10102L, "00102", "Monto Único Consolidado")));
        when(conceptoRepository.findByCodigoMefAndActivo("L003", 1))
                .thenReturn(Optional.of(conceptoBaseRem(10003L, "L003", "Compensación SERVIR (carrera)")));

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
        // FASE 3 — histórico ene-abr (4 meses) para proyección anual Art. 40.
        mockHistorico5ta(4102.50, 4);

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
        // FASE 3 Art.40 — bruta = 16410 (hist) + 4102.50×8 = 49230 ; neta = 11780 ;
        // impAnual = 11780×0.08 = 942.40 ; divisor mayo = 8 → 117.80
        assertThat(detallePorConcepto(capt, 5101L).getMonto())
                .isCloseTo(117.80, within(0.01));

        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalIngresos()).isCloseTo(4102.50, within(0.01));
        // descuentos = aporte 410.25 + comisión 65.64 + prima 71.38 + 5ta 117.80 = 665.07
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(665.07, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(4102.50 - 665.07, within(0.01));
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
        mockHistorico5ta(5000.0, 4); // ene-abr percibido → proyección anual

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + 5ta(5101) + ESSALUD(601) = 3
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(3)).save(capt.capture());

        // Art.40: bruta = 20000 (hist) + 5000×8 = 60000 ; neta = 22550 ;
        // impAnual = 22550×0.08 = 1804 ; divisor mayo = 8 → 225.50
        assertThat(detallePorConcepto(capt, 5101L).getMonto())
                .isCloseTo(225.50, within(0.01));

        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(225.50, within(0.01));
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
        mockHistorico5ta(15000.0, 4); // ene-abr percibido → proyección anual

        service.generar(EMPLEADO_ID, PERIODO);

        // detalles: sueldo + 5ta(5101) + ESSALUD(601) = 3
        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, times(3)).save(capt.capture());

        // Art.40: bruta = 60000 (hist) + 15000×8 = 180000 ; rentaNeta = 142550
        // tramo1 26750×0.08=2140 ; tramo2 80250×0.14=11235 ; tramo3 35550×0.17=6043.50
        // impuestoAnual = 19418.50 ; divisor mayo = 8 → 2427.31
        assertThat(detallePorConcepto(capt, 5101L).getMonto())
                .isCloseTo(2427.31, within(0.01));
    }

    // ==================================================================
    // FASE 3 — IR 5ta categoría método Art. 40 (D.S. 122-94-EF)
    // ==================================================================

    /**
     * Caso maestro AGUIRRE (Excel fila 5). Núcleo puro del Art. 40:
     * bruta 177 211.14 − 7×5500 = 138 711.14 → impAnual 18 630.89 ;
     * (18 630.89 − retenido 5 914.31) / divisor mayo 8 = 1 589.57.
     */
    @Test
    void ir5ta_art40_caso_maestro_aguirre_1589_57() {
        GeneradorPlanillaService.Ir5taResultado r = service.calcularRetencion5taArt40(
                new BigDecimal("177211.14"), new BigDecimal("5914.31"),
                /*mes=*/5, new BigDecimal("5500"), 2026);

        assertThat(r.rentaNeta).isEqualByComparingTo("138711.14");
        assertThat(r.impuestoAnual).isEqualByComparingTo("18630.89");
        assertThat(r.divisor).isEqualByComparingTo("8");
        assertThat(r.retencionMes).isCloseTo(new BigDecimal("1589.57"), within(new BigDecimal("0.01")));
    }

    /** Bajo 7 UIT → exonerado (retención 0), aun con divisor del mes. */
    @Test
    void ir5ta_art40_bajo_7uit_exonerado() {
        GeneradorPlanillaService.Ir5taResultado r = service.calcularRetencion5taArt40(
                new BigDecimal("30000"), BigDecimal.ZERO, 5, new BigDecimal("5500"), 2026);

        assertThat(r.retencionMes).isEqualByComparingTo("0");
    }

    /** Diciembre: divisor = 1 → la retención del mes es el saldo completo. */
    @Test
    void ir5ta_art40_diciembre_divisor_uno() {
        when(parametroService.obtenerValorOpcional(eq("IR5TA_DIVISOR_MES_12"), anyInt(), any()))
                .thenReturn(Optional.of(BigDecimal.ONE));

        GeneradorPlanillaService.Ir5taResultado r = service.calcularRetencion5taArt40(
                new BigDecimal("177211.14"), new BigDecimal("17000"),
                /*mes=*/12, new BigDecimal("5500"), 2026);

        // impAnual 18 630.89 − 17 000 = 1 630.89 ; /1 = 1 630.89
        assertThat(r.divisor).isEqualByComparingTo("1");
        assertThat(r.retencionMes).isCloseTo(new BigDecimal("1630.89"), within(new BigDecimal("0.01")));
    }

    /**
     * Aguinaldos proyectados: con IR5TA_INCLUYE_AGUINALDO=1 y factor=1, en mayo
     * se proyectan 2 aguinaldos (FP + Navidad), elevando la base anual.
     * bruta = 20000 (hist) + 5000×8 + 5000×2 = 70000 ; neta = 32550 ;
     * impAnual = 2140 + 5800×0.14(=812) = 2952 ; /8 = 369.00.
     */
    @Test
    void ir5ta_art40_aguinaldos_proyectados_elevan_base() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(5000.0, REG_728, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(5000.0);
        mockHistorico5ta(5000.0, 4);
        when(parametroService.obtenerValorOpcional(eq("IR5TA_INCLUYE_AGUINALDO"), anyInt(), any()))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(parametroService.obtenerValorOpcional(eq("IR5TA_AGUINALDO_FACTOR"), anyInt(), any()))
                .thenReturn(Optional.of(BigDecimal.ONE));

        service.generar(EMPLEADO_ID, PERIODO);

        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, atLeastOnce()).save(capt.capture());
        assertThat(detallePorConcepto(capt, 5101L).getMonto())
                .isCloseTo(369.00, within(0.01));
    }

    /** CAS (D.Leg. 1057) nunca retiene 5ta (LEY-03), aun con renta alta. */
    @Test
    void ir5ta_cas_nunca_retiene() {
        when(regimenLaboralRepository.findById(REG_CAS))
                .thenReturn(Optional.of(regimenLaboral("CAS")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(20000.0, REG_CAS, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(20000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, atLeastOnce()).save(capt.capture());
        assertThat(capt.getAllValues())
                .noneMatch(d -> Long.valueOf(5101L).equals(d.getConceptoPlanillaId()));
    }

    /**
     * Histórico ene-{@code meses} con totalIngresos mensual = {@code ingresoMensual}.
     * Permite proyectar la base anual del Art. 40 en tests de mayo.
     */
    private void mockHistorico5ta(double ingresoMensual, int meses) {
        java.util.List<MovimientoPlanilla> previos = new java.util.ArrayList<>();
        for (int m = 1; m <= meses; m++) {
            MovimientoPlanilla mv = new MovimientoPlanilla();
            mv.setId(7000L + m);
            mv.setEmpleadoId(EMPLEADO_ID);
            mv.setPeriodo(String.format("2026-%02d", m));
            mv.setActivo(1);
            mv.setTotalIngresos(ingresoMensual);
            previos.add(mv);
        }
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(previos);
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

    @Test
    void regla50_cas_resta_ir4ta_para_validar_neto() {
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", true);
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(5364.19, REG_CAS, /*asigFam=*/0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());

        ConceptoPlanilla descVol = conceptoMef(701L, "05310", "Otros Descuentos", "DESCUENTO");
        when(conceptoRepository.findById(701L)).thenReturn(Optional.of(descVol));
        EmpleadoConcepto ecDesc = new EmpleadoConcepto();
        ecDesc.setEmpleadoId(EMPLEADO_ID);
        ecDesc.setConceptoPlanillaId(701L);
        ecDesc.setMonto(2450.0);
        ecDesc.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(ecDesc));
        when(parametroService.obtenerValorOpcional(eq("BASE_INAFECTA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("1500")));
        when(parametroService.obtenerValorOpcional(eq("TASA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.08")));
        when(suspension4taService.consultarVigente(eq(EMPLEADO_ID), any()))
                .thenReturn(Suspension4taVigenteDto.noRegistrada());
        when(conceptoRepository.findByCodigoAndActivo("IR4TA_CAS", 1))
                .thenReturn(Optional.of(conceptoMef(9042L, "NO_APLICA", "Retención IR 4ta CAS", "DESCUENTO")));

        service.generar(EMPLEADO_ID, PERIODO);

        // umbral = (5364.19 - 429.14 IR4ta) * 0.5 = 2467.53.
        // neto   = 5364.19 - 429.14 - 2450 = 2485.05 -> BIEN.
        // Sin restar IR4ta, el umbral seria 2682.10 y marcaria NETO_NO_VA.
        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getNeto50pctMinimo()).isCloseTo(2467.53, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(2485.05, within(0.01));
        assertThat(cabecera.getEstadoNeto()).isEqualTo("BIEN");
    }

    @Test
    void regla50_resta_onp_y_descuento_judicial_real_para_validar_neto() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));

        ConceptoPlanilla judicial =
                conceptoMef(716L, "NO_APLICA", "Descuento judicial", "DESCUENTO_JUDICIAL");
        judicial.setCodigo("DESCUENTO_JUDICIAL");
        judicial.setCodigoSisper("716");
        ConceptoPlanilla descVol = conceptoMef(702L, "05310", "Otros Descuentos", "DESCUENTO");
        when(conceptoRepository.findById(716L)).thenReturn(Optional.of(judicial));
        when(conceptoRepository.findById(702L)).thenReturn(Optional.of(descVol));

        EmpleadoConcepto ecJudicial = new EmpleadoConcepto();
        ecJudicial.setEmpleadoId(EMPLEADO_ID);
        ecJudicial.setConceptoPlanillaId(716L);
        ecJudicial.setMonto(1000.0);
        ecJudicial.setActivo(1);
        EmpleadoConcepto ecDesc = new EmpleadoConcepto();
        ecDesc.setEmpleadoId(EMPLEADO_ID);
        ecDesc.setConceptoPlanillaId(702L);
        ecDesc.setMonto(750.0);
        ecDesc.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(ecJudicial, ecDesc));

        service.generar(EMPLEADO_ID, PERIODO);

        // umbral = (3000 - 390 ONP - 1000 judicial) * 0.5 = 805.
        // neto   = 3000 - 390 - 1000 - 750 = 860 -> BIEN.
        // Sin restar judicial, el umbral seria 1305 y marcaria NETO_NO_VA.
        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getNeto50pctMinimo()).isCloseTo(805.00, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(860.00, within(0.01));
        assertThat(cabecera.getEstadoNeto()).isEqualTo("BIEN");
    }

    @Test
    void regla50_resta_aporte_afp_real_para_validar_neto() {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_AFP_ID, null, null, null)));

        ConceptoPlanilla descVol = conceptoMef(703L, "05310", "Otros Descuentos", "DESCUENTO");
        when(conceptoRepository.findById(703L)).thenReturn(Optional.of(descVol));
        EmpleadoConcepto ecDesc = new EmpleadoConcepto();
        ecDesc.setEmpleadoId(EMPLEADO_ID);
        ecDesc.setConceptoPlanillaId(703L);
        ecDesc.setMonto(1300.0);
        ecDesc.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(ecDesc));

        service.generar(EMPLEADO_ID, PERIODO);

        // AFP real = 300 aporte + 41.10 prima = 341.10.
        // umbral = (3000 - 341.10 AFP) * 0.5 = 1329.45.
        // neto   = 3000 - 341.10 - 1300 = 1358.90 -> BIEN.
        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getNeto50pctMinimo()).isCloseTo(1329.45, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(1358.90, within(0.01));
        assertThat(cabecera.getEstadoNeto()).isEqualTo("BIEN");
    }

    @Test
    void pensionista_no_genera_aporte_pensionario() {
        assertRegimenPensionarioSinAporte(REG_PENS_PENSIONISTA_ID);
    }

    @Test
    void retiro_no_genera_aporte_pensionario() {
        assertRegimenPensionarioSinAporte(REG_PENS_RETIRO_ID);
    }

    @Test
    void sin_regimen_no_genera_aporte_pensionario() {
        assertRegimenPensionarioSinAporte(REG_PENS_SIN_REGIMEN_ID);
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

    /** Concepto de remuneración base del régimen (afecto a pensión, ESSALUD y 5ta). */
    private ConceptoPlanilla conceptoBaseRem(Long id, String mef, String nombre) {
        ConceptoPlanilla c = conceptoMef(id, mef, nombre, "REMUNERATIVO");
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

    private void assertRegimenPensionarioSinAporte(Long regimenPensionarioId) {
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(regimenPensionarioId, null, null, null)));

        service.generar(EMPLEADO_ID, PERIODO);

        MovimientoPlanilla cabecera = capturarCabeceraFinal();
        assertThat(cabecera.getTotalIngresos()).isCloseTo(3000.00, within(0.01));
        assertThat(cabecera.getTotalDescuentos()).isCloseTo(0.00, within(0.01));
        assertThat(cabecera.getNetoPagar()).isCloseTo(3000.00, within(0.01));
        assertThat(cabecera.getNeto50pctMinimo()).isCloseTo(1500.00, within(0.01));
    }

    // ======================================================================
    // F1.3 — Tests del prorrateo por días laborados (helpers nuevos).
    //
    // Estos tests no ejercen el flujo completo de generar(); validan los
    // helpers `prorratear()` y `calcularDiasLaborados()` directamente.
    // ======================================================================

    // ---- F1.3c prorratear() ----

    @Test
    void prorratear_mes_completo_devuelve_monto_original() {
        BigDecimal monto = new BigDecimal("5500.00");
        assertThat(GeneradorPlanillaService.prorratear(monto, 30))
                .isEqualByComparingTo("5500.00");
    }

    @Test
    void prorratear_mas_de_30_dias_no_excede_monto_original() {
        // No debería pasar en la práctica (días > 30) pero por defensa de invariantes.
        BigDecimal monto = new BigDecimal("5500.00");
        assertThat(GeneradorPlanillaService.prorratear(monto, 31))
                .isEqualByComparingTo("5500.00");
    }

    @Test
    void prorratear_15_dias_es_mitad_del_monto() {
        BigDecimal monto = new BigDecimal("5500.00");
        assertThat(GeneradorPlanillaService.prorratear(monto, 15))
                .isEqualByComparingTo("2750.00");
    }

    @Test
    void prorratear_25_dias_redondea_a_dos_decimales_half_up() {
        // 5500 / 30 * 25 = 4583.333... -> 4583.33 (HALF_UP)
        BigDecimal monto = new BigDecimal("5500.00");
        assertThat(GeneradorPlanillaService.prorratear(monto, 25))
                .isEqualByComparingTo("4583.33");
    }

    @Test
    void prorratear_cero_dias_es_cero() {
        assertThat(GeneradorPlanillaService.prorratear(new BigDecimal("5500"), 0))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void prorratear_dias_negativos_es_cero() {
        assertThat(GeneradorPlanillaService.prorratear(new BigDecimal("5500"), -5))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void prorratear_monto_null_es_cero() {
        assertThat(GeneradorPlanillaService.prorratear(null, 15))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void prorratear_monto_cero_es_cero() {
        assertThat(GeneradorPlanillaService.prorratear(BigDecimal.ZERO, 15))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- F1.3b calcularDiasLaborados() ----

    @Test
    void calcularDiasLaborados_sin_asistencia_devuelve_30() {
        when(asistenciaCabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(30);
    }

    @Test
    void calcularDiasLaborados_asistencia_no_validada_devuelve_30() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEstado("BORRADOR");
        cab.setDiasFalta(10); // se ignora porque no está VALIDADA
        when(asistenciaCabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(cab));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(30);
    }

    @Test
    void calcularDiasLaborados_con_5_faltas_validadas_devuelve_25() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEstado("VALIDADA");
        cab.setDiasFalta(5);
        when(asistenciaCabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(cab));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(25);
    }

    @Test
    void calcularDiasLaborados_30_o_mas_faltas_devuelve_0_no_negativo() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEstado("VALIDADA");
        cab.setDiasFalta(40);
        when(asistenciaCabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(cab));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isZero();
    }

    @Test
    void calcularDiasLaborados_diasFalta_null_se_trata_como_cero() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEstado("VALIDADA");
        cab.setDiasFalta(null);
        when(asistenciaCabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(cab));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(30);
    }

    // ---- F1.4c calcularReintegro() ----

    @Test
    void calcularReintegro_sin_reintegro_devuelve_cero() {
        when(empleadoReintegroRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());

        BigDecimal r = service.calcularReintegro(EMPLEADO_ID, PERIODO,
                new BigDecimal("5500.00"));
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calcularReintegro_15_dias_es_mitad_de_la_base() {
        EmpleadoReintegro r = new EmpleadoReintegro();
        r.setDiasReintegro(15);
        when(empleadoReintegroRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(r));

        BigDecimal monto = service.calcularReintegro(EMPLEADO_ID, PERIODO,
                new BigDecimal("5500.00"));
        assertThat(monto).isEqualByComparingTo("2750.00");
    }

    @Test
    void calcularReintegro_30_dias_devuelve_base_completa() {
        EmpleadoReintegro r = new EmpleadoReintegro();
        r.setDiasReintegro(30);
        when(empleadoReintegroRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(r));

        BigDecimal monto = service.calcularReintegro(EMPLEADO_ID, PERIODO,
                new BigDecimal("5500.00"));
        assertThat(monto).isEqualByComparingTo("5500.00");
    }

    @Test
    void calcularReintegro_base_cero_devuelve_cero_aunque_haya_dias() {
        EmpleadoReintegro r = new EmpleadoReintegro();
        r.setDiasReintegro(20);
        when(empleadoReintegroRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(r));

        BigDecimal monto = service.calcularReintegro(EMPLEADO_ID, PERIODO,
                BigDecimal.ZERO);
        assertThat(monto).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calcularReintegro_repo_solo_devuelve_filas_activas() {
        // El motor SIEMPRE consulta con ACTIVO=1; verificamos que llama así.
        when(empleadoReintegroRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());

        service.calcularReintegro(EMPLEADO_ID, PERIODO, new BigDecimal("5500"));

        verify(empleadoReintegroRepository, times(1))
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1);
    }

    // ======================================================================
    // F1.5a — Feature flag motor.v3.prorrateo.enabled.
    //
    // El flag se inyecta vía @Value; en tests lo seteamos por ReflectionTestUtils
    // sobre la instancia del service (mismo patrón que `self`).
    // ======================================================================

    @Test
    void flag_off_motor_no_consulta_repo_reintegro_aunque_haya_uno_vigente() {
        // Default: flag OFF. Aunque haya un reintegro en BD el motor lo ignora.
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", false);

        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        mockSueldo(3000.0);

        service.generar(EMPLEADO_ID, PERIODO);

        // Cero llamadas al repo de reintegro porque el flag bloquea la rama.
        verify(empleadoReintegroRepository, times(0))
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1);
    }

    @Test
    void flag_on_motor_consulta_repo_reintegro_y_no_suma_si_no_hay() {
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", true);

        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        mockSueldo(3000.0);
        // Sin reintegro vigente.
        when(empleadoReintegroRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());

        service.generar(EMPLEADO_ID, PERIODO);

        // El motor consultó el repo (rama activa) pero no sumó nada porque
        // no había fila vigente.
        verify(empleadoReintegroRepository, times(1))
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1);

        // Sueldo base 3000 → totalIngresos == 3000 (sin reintegro).
        MovimientoPlanilla cab = capturarCabeceraFinal();
        assertThat(cab.getTotalIngresos()).isEqualTo(3000.00, within(0.01));
    }

    @Test
    void flag_on_con_reintegro_15_dias_suma_mitad_del_sueldo_a_totalIngresos() {
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", true);

        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        mockSueldo(3000.0);

        EmpleadoReintegro reint = new EmpleadoReintegro();
        reint.setDiasReintegro(15);
        when(empleadoReintegroRepository
                .findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(reint));

        service.generar(EMPLEADO_ID, PERIODO);

        // Sueldo 3000 + reintegro 15d sobre base 3000 = 3000 + 1500 = 4500.
        MovimientoPlanilla cab = capturarCabeceraFinal();
        assertThat(cab.getTotalIngresos()).isEqualTo(4500.00, within(0.01));
    }

    // ======================================================================
    // F1.6 — Tope 45% UIT en EsSalud SOLO para régimen CAS (decisión RRHH C2).
    //
    // Tests directos del helper `aplicarTopeEssaludCAS()` — no requiere ejercer
    // todo `generar()`. Verifican: el régimen filtra correctamente y el parámetro
    // se lee de BD vía ParametroRemunerativoService.
    // ======================================================================

    @Test
    void aplicarTopeEssaludCAS_regimen_CAS_base_supera_tope_se_topea() {
        // UIT 5350, factor 0.45 → tope = 0.45 × 5350 = 2407.50.
        when(parametroService.obtenerValorOpcional(eq("TOPE_ESSALUD_PCT_UIT"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.45")));
        when(parametroService.obtenerValor(eq("UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("5350"));

        BigDecimal base = new BigDecimal("5500.00");
        BigDecimal topeada = service.aplicarTopeEssaludCAS(base, "CAS", 2026);

        assertThat(topeada).isEqualByComparingTo("2407.50");
    }

    @Test
    void aplicarTopeEssaludCAS_regimen_CAS_base_menor_tope_no_se_topea() {
        when(parametroService.obtenerValorOpcional(eq("TOPE_ESSALUD_PCT_UIT"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.45")));
        when(parametroService.obtenerValor(eq("UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("5350"));

        // 1500 < 2407.50 → no topea.
        BigDecimal base = new BigDecimal("1500.00");
        BigDecimal topeada = service.aplicarTopeEssaludCAS(base, "CAS", 2026);

        assertThat(topeada).isEqualByComparingTo("1500.00");
    }

    @Test
    void aplicarTopeEssaludCAS_regimen_728_no_se_topea_aunque_supere_tope() {
        // Aunque haya tope sembrado, el régimen 728 NUNCA se topea.
        BigDecimal base = new BigDecimal("9999.00");
        BigDecimal topeada = service.aplicarTopeEssaludCAS(base, "728", 2026);

        assertThat(topeada).isEqualByComparingTo("9999.00");
    }

    @Test
    void aplicarTopeEssaludCAS_regimen_SERVIR_no_se_topea() {
        BigDecimal base = new BigDecimal("8000.00");
        BigDecimal topeada = service.aplicarTopeEssaludCAS(base, "SERVIR", 2026);

        assertThat(topeada).isEqualByComparingTo("8000.00");
    }

    @Test
    void aplicarTopeEssaludCAS_regimen_276_no_se_topea() {
        BigDecimal base = new BigDecimal("7000.00");
        BigDecimal topeada = service.aplicarTopeEssaludCAS(base, "276", 2026);

        assertThat(topeada).isEqualByComparingTo("7000.00");
    }

    @Test
    void aplicarTopeEssaludCAS_regimen_null_no_se_topea() {
        BigDecimal base = new BigDecimal("5500.00");
        BigDecimal topeada = service.aplicarTopeEssaludCAS(base, null, 2026);

        assertThat(topeada).isEqualByComparingTo("5500.00");
    }

    @Test
    void aplicarTopeEssaludCAS_CAS_sin_parametro_sembrado_no_se_topea() {
        // Defensa: si TOPE_ESSALUD_PCT_UIT no existe en BD, NO bloquea el cálculo.
        when(parametroService.obtenerValorOpcional(eq("TOPE_ESSALUD_PCT_UIT"), anyInt(), any()))
                .thenReturn(Optional.empty());

        BigDecimal base = new BigDecimal("5500.00");
        BigDecimal topeada = service.aplicarTopeEssaludCAS(base, "CAS", 2026);

        assertThat(topeada).isEqualByComparingTo("5500.00");
    }

    @Test
    void aplicarTopeEssaludCAS_base_cero_devuelve_cero() {
        BigDecimal topeada = service.aplicarTopeEssaludCAS(BigDecimal.ZERO, "CAS", 2026);
        assertThat(topeada).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aplicarTopeEssaludCAS_base_null_devuelve_cero_defensivo() {
        BigDecimal topeada = service.aplicarTopeEssaludCAS(null, "CAS", 2026);
        assertThat(topeada).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ======================================================================
    // F1.7 — Retención IR 4ta categoría SOLO régimen CAS (decisión RRHH C1).
    //
    // Tests directos al helper `calcular4taCategoriaCAS()`. La conexión al
    // motor está protegida por motorV3ProrrateoEnabled (mismo flag de F1.5a);
    // el último test ejerce esa rama via generar().
    // ======================================================================

    @Test
    void ir4ta_CAS_base_supera_inafecto_aplica_8pct() {
        // Base 3000 > 1500 (inafecto) → IR4ta = 3000 × 0.08 = 240.00
        when(parametroService.obtenerValorOpcional(eq("BASE_INAFECTA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("1500.00")));
        when(parametroService.obtenerValorOpcional(eq("TASA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.08")));

        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("3000.00"), "CAS", 2026, false);

        assertThat(ir4ta).isEqualByComparingTo("240.00");
    }

    @Test
    void ir4ta_CAS_base_igual_a_inafecto_devuelve_cero() {
        when(parametroService.obtenerValorOpcional(eq("BASE_INAFECTA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("1500.00")));

        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("1500.00"), "CAS", 2026, false);

        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_CAS_base_menor_a_inafecto_devuelve_cero_INAFECTO() {
        when(parametroService.obtenerValorOpcional(eq("BASE_INAFECTA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("1500.00")));

        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("1200.00"), "CAS", 2026, false);

        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_CAS_con_suspension_devuelve_cero_aunque_supere_inafecto() {
        // tieneSuspension4ta = true → no se retiene aunque la base sea alta.
        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("10000.00"), "CAS", 2026, true);

        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_regimen_728_no_aplica_devuelve_cero() {
        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("5500.00"), "728", 2026, false);
        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_regimen_SERVIR_no_aplica_devuelve_cero() {
        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("5500.00"), "SERVIR", 2026, false);
        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_regimen_276_no_aplica_devuelve_cero() {
        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("5500.00"), "276", 2026, false);
        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_regimen_null_no_aplica_devuelve_cero() {
        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("5500.00"), null, 2026, false);
        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_base_null_devuelve_cero_defensivo() {
        BigDecimal ir4ta = service.calcular4taCategoriaCAS(null, "CAS", 2026, false);
        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_CAS_sin_parametro_inafecto_devuelve_cero() {
        // Defensa: si el parámetro BASE_INAFECTA_IR4TA no existe en BD,
        // NO bloquea el cálculo y NO retiene (más seguro).
        when(parametroService.obtenerValorOpcional(eq("BASE_INAFECTA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.empty());

        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("5500.00"), "CAS", 2026, false);

        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ir4ta_CAS_sin_parametro_tasa_devuelve_cero() {
        // Defensa: si BASE_INAFECTA está cargado pero TASA_IR4TA no, tampoco retiene.
        when(parametroService.obtenerValorOpcional(eq("BASE_INAFECTA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("1500.00")));
        when(parametroService.obtenerValorOpcional(eq("TASA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.empty());

        BigDecimal ir4ta = service.calcular4taCategoriaCAS(
                new BigDecimal("5500.00"), "CAS", 2026, false);

        assertThat(ir4ta).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ======================================================================
    // FASE 1 — Cableado del motor (caller bloque 8c): suspensión + línea
    // trazable IR4TA_CAS + cuadre. Concepto resuelto por CODIGO (no MEF).
    // ======================================================================

    /**
     * CAS sin suspensión, base 5364.19 → IR4ta = 429.14 (= base×8%, sin restar
     * BK porque montoNoAfectoIr4ta = 0). Graba línea trazable con el concepto
     * IR4TA_CAS resuelto por CODIGO. NO exige CODIGO_MEF. Test maestro vs Excel.
     */
    @Test
    void ir4ta_cas_sin_suspension_base_5364_19_retiene_429_14_con_linea_trazable() {
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", true);
        when(regimenLaboralRepository.findById(REG_CAS))
                .thenReturn(Optional.of(regimenLaboral("CAS")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(5364.19, REG_CAS, /*asigFam=*/0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(5364.19);
        when(parametroService.obtenerValorOpcional(eq("BASE_INAFECTA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("1500")));
        when(parametroService.obtenerValorOpcional(eq("TASA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.08")));
        when(suspension4taService.consultarVigente(eq(EMPLEADO_ID), any()))
                .thenReturn(Suspension4taVigenteDto.noRegistrada());
        when(conceptoRepository.findByCodigoAndActivo("IR4TA_CAS", 1))
                .thenReturn(Optional.of(conceptoMef(9042L, "NO_APLICA", "Retención IR 4ta CAS", "DESCUENTO")));

        service.generar(EMPLEADO_ID, PERIODO);

        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, atLeastOnce()).save(capt.capture());
        MovimientoPlanillaDetalle linea = detallePorConcepto(capt, 9042L);
        assertThat(linea.getMonto()).isCloseTo(429.14, within(0.01));
        assertThat(linea.getObservacion()).contains("tributoSUNAT=3042");
        verify(suspension4taService).consultarVigente(eq(EMPLEADO_ID), any());

        // FASE 2 — Trazabilidad: se descartó el snapshot previo y se registró
        // al menos GENERAL + IR4TA_CAS (efecto lateral, no altera el cálculo).
        verify(calculoSnapshotService).desactivarPrevios(EMPLEADO_ID, PERIODO);
        verify(calculoSnapshotService, atLeast(2))
                .registrar(any(CalculoSnapshotService.Registro.class));

        // Cuadre: sin pensión, el único descuento es IR4ta.
        MovimientoPlanilla cab = capturarCabeceraFinal();
        assertThat(cab.getTotalDescuentos()).isCloseTo(429.14, within(0.01));
    }

    /** CAS con suspensión vigente → IR4ta = 0, no se graba línea, no bloquea. */
    @Test
    void ir4ta_cas_con_suspension_vigente_no_retiene_ni_graba_linea() {
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", true);
        when(regimenLaboralRepository.findById(REG_CAS))
                .thenReturn(Optional.of(regimenLaboral("CAS")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(5364.19, REG_CAS, /*asigFam=*/0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        mockSueldo(5364.19);
        when(parametroService.obtenerValorOpcional(eq("BASE_INAFECTA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("1500")));
        when(parametroService.obtenerValorOpcional(eq("TASA_IR4TA"), anyInt(), any()))
                .thenReturn(Optional.of(new BigDecimal("0.08")));
        when(suspension4taService.consultarVigente(eq(EMPLEADO_ID), any()))
                .thenReturn(new Suspension4taVigenteDto(true, false, "C-123", null, null));

        service.generar(EMPLEADO_ID, PERIODO);

        // No se resuelve el concepto IR4TA_CAS porque no hay línea que grabar.
        verify(conceptoRepository, never()).findByCodigoAndActivo("IR4TA_CAS", 1);
        MovimientoPlanilla cab = capturarCabeceraFinal();
        assertThat(cab.getTotalDescuentos()).isCloseTo(0.00, within(0.01));
    }

    /**
     * Mejora 2026-06-03 — La remuneración base viene de EmpleadoPlanilla.sueldoBasico
     * (Configuración de planilla). Un concepto base asignado a mano (legacy) con
     * OTRO monto se IGNORA (no duplica ni manda).
     */
    @Test
    void base_remunerativa_viene_de_sueldoBasico_e_ignora_concepto_base_manual() {
        when(regimenLaboralRepository.findById(REG_CAS))
                .thenReturn(Optional.of(regimenLaboral("CAS")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(5000.0, REG_CAS, /*asigFam=*/0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());

        // Concepto base manual (00301) con monto distinto → debe ignorarse.
        EmpleadoConcepto ecBaseLegacy = new EmpleadoConcepto();
        ecBaseLegacy.setEmpleadoId(EMPLEADO_ID);
        ecBaseLegacy.setConceptoPlanillaId(CONCEPTO_SUELDO_ID);
        ecBaseLegacy.setMonto(9999.0);
        ecBaseLegacy.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(ecBaseLegacy));
        when(conceptoRepository.findById(CONCEPTO_SUELDO_ID))
                .thenReturn(Optional.of(conceptoSueldo())); // MEF 00301 = base → se ignora

        service.generar(EMPLEADO_ID, PERIODO);

        MovimientoPlanilla cab = capturarCabeceraFinal();
        // Ingresos = 5000 (de sueldoBasico), NO 9999 del concepto base manual.
        assertThat(cab.getTotalIngresos()).isCloseTo(5000.00, within(0.01));
    }

    @Test
    void base_remunerativa_cas_tambien_aplica_si_regimen_catalogo_es_1057() {
        when(regimenLaboralRepository.findById(REG_CAS))
                .thenReturn(Optional.of(regimenLaboral("1057")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(5364.19, REG_CAS, /*asigFam=*/0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());

        service.generar(EMPLEADO_ID, PERIODO);

        ArgumentCaptor<MovimientoPlanillaDetalle> capt =
                ArgumentCaptor.forClass(MovimientoPlanillaDetalle.class);
        verify(detalleRepository, atLeastOnce()).save(capt.capture());

        MovimientoPlanillaDetalle baseCas = detallePorConcepto(capt, 10501L);
        assertThat(baseCas.getMonto()).isCloseTo(5364.19, within(0.01));
        assertThat(baseCas.getObservacion()).contains("Remuneración base");

        MovimientoPlanilla cab = capturarCabeceraFinal();
        assertThat(cab.getTotalIngresos()).isCloseTo(5364.19, within(0.01));
    }

    // ======================================================================
    // F1.5b — Helper estático regimenAplicaConcepto (parser CSV).
    //
    // Static → no requiere instancia ni mocks.
    // ======================================================================

    @Test
    void regimenAplicaConcepto_TODOS_acepta_cualquier_regimen() {
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("TODOS", "728")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("TODOS", "276")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("todos", "SERVIR")).isTrue();
    }

    @Test
    void regimenAplicaConcepto_null_o_blank_es_compat_TODOS() {
        // Conceptos legacy sin metadata cargada → no bloquear.
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto(null, "728")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("", "728")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("   ", "728")).isTrue();
    }

    @Test
    void regimenAplicaConcepto_valor_unico_coincide() {
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728", "728")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("1057", "1057")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("SERVIR", "SERVIR")).isTrue();
    }

    @Test
    void regimenAplicaConcepto_alias_CAS_equivale_1057() {
        // Mejora 2026-06-03: el catálogo usa 'CAS', los conceptos '1057' → equivalentes.
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("1057", "CAS")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728,1057", "CAS")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("SERVIR", "CAS")).isFalse();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728", "CAS")).isFalse();
    }

    @Test
    void regimenAplicaConcepto_valor_unico_no_coincide() {
        // DS 311 solo 728 → empleado 276 NO acepta.
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728", "276")).isFalse();
        // Asignación familiar régimen 728 (Ley 25129) → empleado CAS NO acepta.
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728", "1057")).isFalse();
    }

    @Test
    void regimenAplicaConcepto_CSV_acepta_si_token_presente() {
        // DS 327 → '728,1057' acepta a ambos.
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728,1057", "728")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728,1057", "1057")).isTrue();
    }

    @Test
    void regimenAplicaConcepto_CSV_rechaza_si_token_ausente() {
        // DS 327 → '728,1057' rechaza al régimen 276 (excluido por MUC).
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728,1057", "276")).isFalse();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728,1057", "SERVIR")).isFalse();
    }

    @Test
    void regimenAplicaConcepto_CSV_tolera_espacios_y_case() {
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728 , 1057", "1057")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto(" 728,1057 ", "728")).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("CAS", "cas")).isTrue();
    }

    @Test
    void regimenAplicaConcepto_empleado_null_no_bloquea() {
        // Defensivo: sin régimen del empleado, no romper el cálculo.
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728", null)).isTrue();
        assertThat(GeneradorPlanillaService.regimenAplicaConcepto("728,1057", null)).isTrue();
    }

    // ======================================================================
    // F1.5b — Motor en PASO 7: validar régimen + prorratear.
    //
    // Tests de integración via generar(). Reusan el setup feliz de los tests
    // existentes (sueldo manual + sin asistencia).
    // ======================================================================

    @Test
    void flag_off_motor_no_valida_regimen_aplicable_aunque_haya_mismatch() {
        // Sin flag, el motor calcula como antes (compat con motor v2).
        // Concepto cuyo régimen aplicable es 728 asignado a empleado 276 → NO explota.
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", false);
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        ConceptoPlanilla c = conceptoSueldo();
        c.setRegimenAplicable("728"); // mismatch: empleado es 276
        when(conceptoRepository.findById(CONCEPTO_SUELDO_ID)).thenReturn(Optional.of(c));
        mockSueldo(3000.0);

        // No lanza nada; el motor sigue como motor v2.
        service.generar(EMPLEADO_ID, PERIODO);
    }

    @Test
    void flag_on_motor_lanza_excepcion_si_regimen_no_aplica() {
        // Con flag, el motor v3 valida y bloquea.
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", true);
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        ConceptoPlanilla c = conceptoSueldo();
        c.setCodigoMef("11214");                  // DS 327
        c.setRegimenAplicable("728,1057");        // norma: solo 728 y 1057
        c.setNombre("Incremento DS 327-2025-EF");
        when(conceptoRepository.findById(CONCEPTO_SUELDO_ID)).thenReturn(Optional.of(c));
        mockSueldo(3000.0);

        assertThatThrownBy(() -> service.generar(EMPLEADO_ID, PERIODO))
                .isInstanceOf(com.indeci.exception.ConceptoRegimenNoAplicableException.class)
                .hasMessageContaining("11214")
                .hasMessageContaining("276");
    }

    @Test
    void flag_on_motor_prorratea_concepto_S_prorrateable_por_dias_laborados() {
        // Flag ON + ES_PRORRATEABLE='S' + 5 días de falta → monto prorrateado a 25/30.
        // 3000 × 25/30 = 2500.00. La BASE viene de Config. planilla (aquí 0 para
        // aislar el prorrateo de un concepto NO-base: incremento remunerativo).
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", true);
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(0.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        ConceptoPlanilla c = conceptoBaseRem(CONCEPTO_SUELDO_ID, "00303", "Horas Extras");
        c.setEsProrrateable("S");
        c.setRegimenAplicable("TODOS");
        when(conceptoRepository.findById(CONCEPTO_SUELDO_ID)).thenReturn(Optional.of(c));
        mockSueldo(3000.0);

        // 5 días de falta validada → diasLaborados = 25.
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEstado("VALIDADA");
        cab.setDiasFalta(5);
        when(asistenciaCabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(cab));

        service.generar(EMPLEADO_ID, PERIODO);

        MovimientoPlanilla mov = capturarCabeceraFinal();
        assertThat(mov.getTotalIngresos()).isEqualTo(2500.00, within(0.01));
    }

    @Test
    void flag_on_motor_no_prorratea_concepto_N_prorrateable() {
        // Flag ON pero ES_PRORRATEABLE='N' → monto NO se prorratea aunque
        // haya días de falta.
        ReflectionTestUtils.setField(service, "motorV3ProrrateoEnabled", true);
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(planilla(3000.0, REG_276, 0)));
        when(empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(pension(REG_PENS_ONP_ID, null, null, null)));
        ConceptoPlanilla c = conceptoSueldo();
        c.setEsProrrateable("N");
        c.setRegimenAplicable("TODOS");
        when(conceptoRepository.findById(CONCEPTO_SUELDO_ID)).thenReturn(Optional.of(c));
        mockSueldo(3000.0);

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEstado("VALIDADA");
        cab.setDiasFalta(5);
        when(asistenciaCabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(cab));

        service.generar(EMPLEADO_ID, PERIODO);

        MovimientoPlanilla mov = capturarCabeceraFinal();
        assertThat(mov.getTotalIngresos()).isEqualTo(3000.00, within(0.01));
    }

    // ======================================================================
    // F2.3 — calcularDiasLaborados consume INDECI_EMPLEADO_EVENTO.
    // ======================================================================

    @Test
    void calcularDiasLaborados_sin_eventos_devuelve_30() {
        // El repo de eventos devuelve lista vacía por default (Mockito).
        // diasLab = 30 - 0 faltas - 0 eventos = 30.
        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(30);
    }

    @Test
    void calcularDiasLaborados_con_evento_5_dias_resta_5() {
        // Empleado con licencia sin goce 5 días → diasLab = 30 - 5 = 25.
        EmpleadoEvento e = new EmpleadoEvento();
        e.setDiasAfectos(5);
        when(empleadoEventoRepository.findVigentesParaMotor(
                eq(EMPLEADO_ID), eq(PERIODO), any(), any()))
                .thenReturn(java.util.List.of(e));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(25);
    }

    @Test
    void calcularDiasLaborados_eventos_y_faltas_se_suman() {
        // 3 faltas + 7 días evento = 10 → diasLab = 20.
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEstado("VALIDADA");
        cab.setDiasFalta(3);
        when(asistenciaCabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(cab));

        EmpleadoEvento e = new EmpleadoEvento();
        e.setDiasAfectos(7);
        when(empleadoEventoRepository.findVigentesParaMotor(
                eq(EMPLEADO_ID), eq(PERIODO), any(), any()))
                .thenReturn(java.util.List.of(e));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(20);
    }

    @Test
    void calcularDiasLaborados_evento_sin_diasAfectos_deriva_de_rango_fechas() {
        // Maternidad 2026-05-10 a 2026-05-19 → 10 días naturales.
        EmpleadoEvento e = new EmpleadoEvento();
        e.setFechaInicio(java.time.LocalDate.of(2026, 5, 10));
        e.setFechaFin(java.time.LocalDate.of(2026, 5, 19));
        e.setDiasAfectos(null); // deriva
        when(empleadoEventoRepository.findVigentesParaMotor(
                eq(EMPLEADO_ID), eq(PERIODO), any(), any()))
                .thenReturn(java.util.List.of(e));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(20);
    }

    @Test
    void calcularDiasLaborados_eventos_30_o_mas_devuelve_0_no_negativo() {
        // Maternidad 30+ días → diasLab nunca negativo.
        EmpleadoEvento e = new EmpleadoEvento();
        e.setDiasAfectos(45);
        when(empleadoEventoRepository.findVigentesParaMotor(
                eq(EMPLEADO_ID), eq(PERIODO), any(), any()))
                .thenReturn(java.util.List.of(e));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isZero();
    }

    @Test
    void calcularDiasLaborados_multiples_eventos_suma_todos() {
        // Permiso 3 días + licencia 4 días + cese 8 días = 15 → diasLab = 15.
        EmpleadoEvento permiso = new EmpleadoEvento();
        permiso.setDiasAfectos(3);
        EmpleadoEvento licencia = new EmpleadoEvento();
        licencia.setDiasAfectos(4);
        EmpleadoEvento cese = new EmpleadoEvento();
        cese.setDiasAfectos(8);
        when(empleadoEventoRepository.findVigentesParaMotor(
                eq(EMPLEADO_ID), eq(PERIODO), any(), any()))
                .thenReturn(java.util.List.of(permiso, licencia, cese));

        assertThat(service.calcularDiasLaborados(EMPLEADO_ID, PERIODO)).isEqualTo(15);
    }
}
