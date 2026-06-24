package com.indeci.rrhh.service.subsidio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.dto.EventoDistribucionMesDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.SubsidioBaseHistorica;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioLiquidacion;
import com.indeci.rrhh.entity.SubsidioReglaFormula;
import com.indeci.rrhh.entity.SubsidioReglaVigencia;
import com.indeci.rrhh.entity.SubsidioTramo;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.repository.SubsidioLiquidacionRepository;
import com.indeci.rrhh.repository.SubsidioReglaFormulaRepository;
import com.indeci.rrhh.repository.SubsidioTramoRepository;
import com.indeci.rrhh.service.support.DistribucionMensualCalculator;
import com.indeci.rrhh.subsidio.SubsidioEstados;
import com.indeci.rrhh.subsidio.formula.SubsidioFormulaEngine;

/**
 * Casos obligatorios de regresión del Excel institucional
 * {@code excel-referencia/Calculo de subsidios.xlsx} (JUNIO 2026): hojas LOAYZA,
 * VARGAS y las dos de NIRLA CHUQUIHUANGA (MELINA y LD).
 *
 * <p>Cada cifra esperada proviene literalmente de la hoja; los comentarios citan
 * la celda. Se validan dos capas:</p>
 * <ul>
 *   <li>el desglose mensual del descanso ({@link DistribucionMensualCalculator}); y</li>
 *   <li>la liquidación del tramo aplicado en planilla
 *       ({@link SubsidioLiquidacionService}): contraprestación, subsidio ESSALUD,
 *       diferencial INDECI y conciliación.</li>
 * </ul>
 *
 * <p>El subsidio diario reconocido por ESSALUD (base/divisor) se inyecta con el
 * valor real del Excel mockeando el motor de fórmulas; aquí se prueba la
 * aritmética de liquidación, no el DSL ni la base histórica.</p>
 *
 * <p><b>Nota CAS:</b> el Excel confirma que en CAS la entidad asume 0 días tanto
 * en enfermedad como en maternidad — ESSALUD paga desde el día 1
 * ({@code DIAS_ENTIDAD = 0}).</p>
 */
@ExtendWith(MockitoExtension.class)
class SubsidioCasosExcelTest {

    private static final Long TRAMO_ID = 700L;
    private static final Long CASO_ID = 70L;
    private static final Long EMPLEADO_ID = 71L;
    private static final Long BASE_ID = 79L;
    private static final Long REGLA_ID = 1L;

    @Mock private SubsidioTramoRepository tramoRepository;
    @Mock private SubsidioCasoRepository casoRepository;
    @Mock private SubsidioLiquidacionRepository liquidacionRepository;
    @Mock private SubsidioBaseHistoricaService baseHistoricaService;
    @Mock private SubsidioParametroResolverService parametroResolver;
    @Mock private SubsidioReglaResolverService reglaResolver;
    @Mock private SubsidioReglaFormulaRepository formulaRepository;
    @Mock private SubsidioValidacionService validacionService;
    @Mock private SubsidioTimelineService timelineService;
    @Mock private SubsidioFormulaEngine formulaEngine;
    @Mock private EmpleadoPlanillaRepository planillaRepository;

    private SubsidioLiquidacionService service;

    @BeforeEach
    void setUp() {
        service = new SubsidioLiquidacionService(
                tramoRepository, casoRepository, liquidacionRepository, baseHistoricaService,
                parametroResolver, reglaResolver, formulaRepository, validacionService,
                timelineService, formulaEngine, planillaRepository, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", "x"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ══════════════════════ LOAYZA — Enfermedad 45 días ══════════════════════

    /** Hoja "1. LOAYZA": 20/03 (12d) + abril (30d) + 01/05–03/05 (3d) = 45. */
    @Test
    void loayza_descanso_enfermedad_se_reparte_12_30_3_dias() {
        LocalDate inicio = LocalDate.of(2026, 3, 20);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 45);

        assertThat(fin).isEqualTo(LocalDate.of(2026, 5, 3));
        List<EventoDistribucionMesDto> tramos =
                DistribucionMensualCalculator.calcular(inicio, fin);
        assertThat(tramos).extracting(EventoDistribucionMesDto::getPeriodo)
                .containsExactly("202603", "202604", "202605");
        assertThat(tramos).extracting(EventoDistribucionMesDto::getDiasSubsidio)
                .containsExactly(12, 30, 3);
        assertThat(DistribucionMensualCalculator.sumarDias(tramos)).isEqualTo(45);
    }

    /**
     * Hoja "1. LOAYZA", tramo MAYO (3 días subsidio, 27 días laborados eq./30)
     * aplicado en planilla JUNIO 2026. Celdas: G10 rem 4364.19; H10 diaria
     * 145.473; J22 ESSALUD diario 80.63; O26 subsidio 242; L10 diferencial 194.42.
     */
    @Test
    void loayza_liquidacion_tramo_mayo_reproduce_excel() {
        SubsidioTramo tramo = tramo("202605", LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3), 3, 27);
        stubLiquidacion(tramo, SubsidioEstados.TIPO_ENFERMEDAD, "DIAS_ENTIDAD_ENF",
                BigDecimal.ZERO, new BigDecimal("80.63"), 4364.19);
        when(tramoRepository.findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(CASO_ID, 1, "S"))
                .thenReturn(List.of(tramo));

        SubsidioLiquidacion liq = service.calcular(TRAMO_ID);

        assertThat(liq.getContraprestacionDiaria()).isEqualByComparingTo("145.473");
        assertThat(liq.getContraprestacionEquivalente()).isEqualByComparingTo("436.42");  // 145.473 × 3
        assertThat(liq.getSubsidioDiarioEssalud()).isEqualByComparingTo("80.63");
        assertThat(liq.getSubsidioEstimado()).isEqualByComparingTo("242");    // 80.63 × 3 = 241.89 → 242
        assertThat(liq.getDiferencialIndeci()).isEqualByComparingTo("194.42"); // 436.42 − 242
        // 436.42 + 145.473 × 27 = 4364.19 (una remuneración mensual)
        assertThat(liq.getConciliacionTotal()).isEqualByComparingTo("4364.19");
    }

    // ══════════════════════ VARGAS — Maternidad 98 días ══════════════════════

    /** Hoja "2. VARGAS": 04/05–31/05 (28d) + jun (30d) + jul (31d) + 01/08–09/08 (9d) = 98. */
    @Test
    void vargas_descanso_maternidad_se_reparte_28_30_31_9_dias() {
        LocalDate inicio = LocalDate.of(2026, 5, 4);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 98);

        assertThat(fin).isEqualTo(LocalDate.of(2026, 8, 9));
        List<EventoDistribucionMesDto> tramos =
                DistribucionMensualCalculator.calcular(inicio, fin);
        assertThat(tramos).extracting(EventoDistribucionMesDto::getPeriodo)
                .containsExactly("202605", "202606", "202607", "202608");
        assertThat(tramos).extracting(EventoDistribucionMesDto::getDiasSubsidio)
                .containsExactly(28, 30, 31, 9);
        assertThat(DistribucionMensualCalculator.sumarDias(tramos)).isEqualTo(98);
    }

    /**
     * Hoja "2. VARGAS", tramo JUNIO (30 días subsidio, 0 laborados). Base con
     * meses en LSGR=0 → promedio bajo. Celdas: G10 rem 3864.19; H10 diaria
     * 128.8063; J22 ESSALUD diario 56.93; O26 subsidio 1708; L10 diferencial
     * 2156.19.
     */
    @Test
    void vargas_liquidacion_tramo_junio_reproduce_excel() {
        SubsidioTramo tramo = tramo("202606", LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30), 30, 0);
        stubLiquidacion(tramo, SubsidioEstados.TIPO_MATERNIDAD, "DIAS_ENTIDAD_MAT",
                BigDecimal.ZERO, new BigDecimal("56.93"), 3864.19);

        SubsidioLiquidacion liq = service.calcular(TRAMO_ID);

        assertThat(liq.getContraprestacionDiaria()).isEqualByComparingTo("128.8063"); // 3864.19/30
        assertThat(liq.getContraprestacionEquivalente()).isEqualByComparingTo("3864.19"); // ×30
        assertThat(liq.getSubsidioDiarioEssalud()).isEqualByComparingTo("56.93");
        assertThat(liq.getSubsidioEstimado()).isEqualByComparingTo("1708");   // 56.93 × 30 = 1707.9 → 1708
        assertThat(liq.getDiferencialIndeci()).isEqualByComparingTo("2156.19"); // 3864.19 − 1708
        assertThat(liq.getConciliacionTotal()).isEqualByComparingTo("3864.19"); // 0 días laborados
    }

    // ══════════════════ NIRLA CHUQUIHUANGA — Maternidad 98 días ═══════════════

    /**
     * Hoja "3. NIRLA (MELINA)": el CITT inicia el 02/03/2026 (serial 46083), no el
     * 01/03. Arrancar mal (01/03) con el mismo fin (07/06) da 99 días; arrancar
     * bien (02/03) da los 98 correctos. Este es el error que ilustra el caso.
     */
    @Test
    void nirla_melina_inconsistencia_inicio_01_03_vs_02_03() {
        LocalDate finCitt = LocalDate.of(2026, 6, 7); // serial 46180

        // Correcto: descanso desde 02/03 → 30+30+31+7 = 98.
        List<EventoDistribucionMesDto> correcto =
                DistribucionMensualCalculator.calcular(LocalDate.of(2026, 3, 2), finCitt);
        assertThat(correcto).extracting(EventoDistribucionMesDto::getDiasSubsidio)
                .containsExactly(30, 30, 31, 7);
        assertThat(DistribucionMensualCalculator.sumarDias(correcto)).isEqualTo(98);
        assertThat(DistribucionMensualCalculator.calcularFechaFin(LocalDate.of(2026, 3, 2), 98))
                .isEqualTo(finCitt);

        // Erróneo: arrancar el 01/03 con el mismo fin infla marzo a 31 → total 99.
        List<EventoDistribucionMesDto> erroneo =
                DistribucionMensualCalculator.calcular(LocalDate.of(2026, 3, 1), finCitt);
        assertThat(DistribucionMensualCalculator.sumarDias(erroneo)).isEqualTo(99);
    }

    /**
     * Hoja "3. NIRLA (LD)", tramo JUNIO (7 días subsidio, 23 laborados eq./30).
     * Celdas: G10 rem 6800; H10 diaria 226.6667; I10 total 100% 1586.67; J10
     * ESSALUD diario 80.63; K10 ESSALUD 564; L10 diferencial 1022.67.
     */
    @Test
    void nirla_ld_liquidacion_tramo_junio_reproduce_excel() {
        SubsidioTramo tramo = tramo("202606", LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 7), 7, 23);
        stubLiquidacion(tramo, SubsidioEstados.TIPO_MATERNIDAD, "DIAS_ENTIDAD_MAT",
                BigDecimal.ZERO, new BigDecimal("80.63"), 6800.00);

        SubsidioLiquidacion liq = service.calcular(TRAMO_ID);

        assertThat(liq.getContraprestacionDiaria()).isEqualByComparingTo("226.6667"); // 6800/30
        assertThat(liq.getContraprestacionEquivalente()).isEqualByComparingTo("1586.67"); // ×7
        assertThat(liq.getSubsidioDiarioEssalud()).isEqualByComparingTo("80.63");
        assertThat(liq.getSubsidioEstimado()).isEqualByComparingTo("564");    // 80.63 × 7 = 564.41 → 564
        assertThat(liq.getDiferencialIndeci()).isEqualByComparingTo("1022.67"); // 1586.67 − 564
        // 1586.67 + 226.6667 × 23 = 6800.00 (una remuneración mensual) — Excel R18.
        assertThat(liq.getConciliacionTotal()).isEqualByComparingTo("6800.00");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Cablea el camino feliz de cálculo con los datos del caso del Excel. */
    private void stubLiquidacion(SubsidioTramo tramo, String tipoCaso, String paramDiasEntidad,
                                 BigDecimal diasEntidad, BigDecimal subsidioDiarioEssalud,
                                 double sueldoBasico) {
        LocalDate fechaDesde = tramo.getFechaDesde();
        when(tramoRepository.findByIdAndActivo(TRAMO_ID, 1)).thenReturn(Optional.of(tramo));
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso(tipoCaso)));
        when(validacionService.validarCaso(CASO_ID)).thenReturn(List.of());
        when(liquidacionRepository.findByTramoIdAndEsVigente(TRAMO_ID, "S"))
                .thenReturn(Optional.empty());
        when(baseHistoricaService.obtenerVigente(CASO_ID)).thenReturn(base());
        when(baseHistoricaService.listarDetalle(BASE_ID)).thenReturn(List.of());
        when(reglaResolver.resolverVigente(fechaDesde)).thenReturn(regla());
        Map<String, BigDecimal> params = new HashMap<>();
        params.put("TOPE_MENSUAL", new BigDecimal("2475.00"));
        params.put(paramDiasEntidad, diasEntidad);
        when(parametroResolver.mapaNumerico(eq(fechaDesde), anyInt())).thenReturn(params);
        when(formulaRepository.findByReglaVigenciaIdAndCodigoFormulaAndActivo(
                REGLA_ID, "SUBSIDIO_DIARIO_ESSALUD", 1)).thenReturn(Optional.of(formula()));
        when(formulaEngine.evaluar(any(), any())).thenReturn(subsidioDiarioEssalud);
        when(planillaRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(planilla(sueldoBasico)));
        when(liquidacionRepository.findByTramoIdOrderByVersionLiqDesc(TRAMO_ID))
                .thenReturn(List.of());
        when(parametroResolver.idsVersionVigente(any())).thenReturn(List.of(5L));
        when(liquidacionRepository.save(any())).thenAnswer(inv -> {
            SubsidioLiquidacion l = inv.getArgument(0);
            l.setId(900L);
            return l;
        });
        when(tramoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(casoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static SubsidioTramo tramo(String periodo, LocalDate desde, LocalDate hasta,
                                       int diasSubsidio, int diasLaborados) {
        SubsidioTramo t = new SubsidioTramo();
        t.setId(TRAMO_ID);
        t.setCasoId(CASO_ID);
        t.setPeriodo(periodo);
        t.setFechaDesde(desde);
        t.setFechaHasta(hasta);
        t.setDiasSubsidio(diasSubsidio);
        t.setDiasLaborados(diasLaborados);
        t.setEstadoTramo(SubsidioEstados.TRAMO_BORRADOR);
        t.setEsVigente("S");
        t.setActivo(1);
        return t;
    }

    private static SubsidioCaso caso(String tipoCaso) {
        SubsidioCaso c = new SubsidioCaso();
        c.setId(CASO_ID);
        c.setEmpleadoId(EMPLEADO_ID);
        c.setTipoCaso(tipoCaso);
        c.setEstado(SubsidioEstados.CASO_BORRADOR);
        return c;
    }

    private static SubsidioBaseHistorica base() {
        SubsidioBaseHistorica b = new SubsidioBaseHistorica();
        b.setId(BASE_ID);
        b.setCasoId(CASO_ID);
        b.setDivisorPromedio(360);
        b.setTopeMensual(new BigDecimal("2475.00"));
        b.setMesesEvaluados(12);
        b.setBaseReconocida(new BigDecimal("29025.00"));
        return b;
    }

    private static SubsidioReglaVigencia regla() {
        SubsidioReglaVigencia r = new SubsidioReglaVigencia();
        r.setId(REGLA_ID);
        r.setCodigo("SUB_REGLA");
        r.setVersion("2026.1");
        return r;
    }

    private static SubsidioReglaFormula formula() {
        SubsidioReglaFormula f = new SubsidioReglaFormula();
        f.setId(7L);
        f.setReglaVigenciaId(REGLA_ID);
        f.setCodigoFormula("SUBSIDIO_DIARIO_ESSALUD");
        f.setExpresionJson("{}");
        f.setActivo(1);
        return f;
    }

    private static EmpleadoPlanilla planilla(double sueldoBasico) {
        EmpleadoPlanilla p = new EmpleadoPlanilla();
        p.setEmpleadoId(EMPLEADO_ID);
        p.setSueldoBasico(sueldoBasico);
        p.setActivo(1);
        return p;
    }
}
