package com.indeci.rrhh.service.subsidio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.subsidio.SubsidioValidacionDto;
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
import com.indeci.rrhh.subsidio.SubsidioEstados;
import com.indeci.rrhh.subsidio.formula.SubsidioFormulaEngine;

/**
 * Tests del núcleo de liquidación de subsidios (enfermedad/incapacidad temporal
 * y maternidad). Verifica el reparto de días entidad/ESSALUD, la contraprestación
 * diaria (rem/30), el diferencial INDECI, el versionado y los bloqueos normativos.
 *
 * <p>El motor de fórmulas se mockea: aquí se prueba la aritmética de liquidación,
 * no el DSL (cubierto por {@code SubsidioFormulaEngineTest}).</p>
 */
@ExtendWith(MockitoExtension.class)
class SubsidioLiquidacionServiceTest {

    private static final Long TRAMO_ID = 100L;
    private static final Long CASO_ID = 10L;
    private static final Long EMPLEADO_ID = 41L;
    private static final Long BASE_ID = 99L;
    private static final Long REGLA_ID = 1L;
    private static final LocalDate FECHA_DESDE = LocalDate.of(2026, 5, 1);

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
                tramoRepository,
                casoRepository,
                liquidacionRepository,
                baseHistoricaService,
                parametroResolver,
                reglaResolver,
                formulaRepository,
                validacionService,
                timelineService,
                formulaEngine,
                planillaRepository,
                new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", "x"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Caso feliz: ENFERMEDAD ────────────────────────────────────────────────

    @Test
    void enfermedad_reparte_dias_entidad_y_essalud_y_calcula_diferencial() {
        SubsidioTramo tramo = tramo(30, 0);
        stubCargaComun(SubsidioEstados.TIPO_ENFERMEDAD, tramo);
        // 20 días los asume la entidad → 10 quedan a cargo de ESSALUD.
        Map<String, BigDecimal> params = paramsBase();
        params.put("DIAS_ENTIDAD_ENF", new BigDecimal("20"));
        when(parametroResolver.mapaNumerico(eq(FECHA_DESDE), anyInt())).thenReturn(params);
        when(formulaEngine.evaluar(any(), any())).thenReturn(new BigDecimal("150"));
        when(planillaRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(planilla(3000.0)));
        // Sin tramos previos: el único tramo vigente es el actual.
        when(tramoRepository.findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(CASO_ID, 1, "S"))
                .thenReturn(List.of(tramo));
        when(liquidacionRepository.findByTramoIdOrderByVersionLiqDesc(TRAMO_ID))
                .thenReturn(List.of());
        stubGuardado();

        SubsidioLiquidacion liq = service.calcular(TRAMO_ID);

        // rem/30 = 100.0000 ; equivalente = 100 * 30 = 3000.00
        assertThat(liq.getContraprestacionDiaria()).isEqualByComparingTo("100.0000");
        assertThat(liq.getContraprestacionEquivalente()).isEqualByComparingTo("3000.00");
        assertThat(liq.getSubsidioDiarioEssalud()).isEqualByComparingTo("150");
        // 150 * 10 días ESSALUD = 1500
        assertThat(liq.getSubsidioEstimado()).isEqualByComparingTo("1500");
        assertThat(liq.getSubsidioReconocido()).isEqualByComparingTo("1500");
        // 3000.00 - 1500 = 1500.00 que asume INDECI
        assertThat(liq.getDiferencialIndeci()).isEqualByComparingTo("1500.00");
        // sin días laborados, la conciliación iguala la contraprestación equivalente
        assertThat(liq.getConciliacionTotal()).isEqualByComparingTo("3000.00");
        assertThat(liq.getVersionLiq()).isEqualTo(1);
        assertThat(liq.getEsVigente()).isEqualTo("S");
        assertThat(liq.getEstado()).isEqualTo(SubsidioEstados.LIQ_CALCULADO);
        assertThat(liq.getFormulaAplicada()).isEqualTo("SUBSIDIO_DIARIO_ESSALUD");
        assertThat(liq.getSnapshotJson()).contains("\"diasEssalud\":10", "ENFERMEDAD");

        // El tramo y el caso avanzan de estado y queda traza en el timeline.
        assertThat(tramo.getEstadoTramo()).isEqualTo(SubsidioEstados.TRAMO_CALCULADO);
        verify(casoRepository).save(any(SubsidioCaso.class));
        verify(timelineService).registrar(eq(CASO_ID), eq("CALCULO_LIQUIDACION"), any(), any());
    }

    // ── Maternidad: usa el parámetro de días entidad de maternidad ────────────

    @Test
    void maternidad_usa_dias_entidad_maternidad_no_enfermedad() {
        SubsidioTramo tramo = tramo(30, 0);
        stubCargaComun(SubsidioEstados.TIPO_MATERNIDAD, tramo);
        Map<String, BigDecimal> params = paramsBase();
        // Si tomara el de enfermedad (99) los días ESSALUD serían 0; debe tomar MAT (20).
        params.put("DIAS_ENTIDAD_MAT", new BigDecimal("20"));
        params.put("DIAS_ENTIDAD_ENF", new BigDecimal("99"));
        when(parametroResolver.mapaNumerico(eq(FECHA_DESDE), anyInt())).thenReturn(params);
        when(formulaEngine.evaluar(any(), any())).thenReturn(new BigDecimal("150"));
        when(planillaRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(planilla(3000.0)));
        when(liquidacionRepository.findByTramoIdOrderByVersionLiqDesc(TRAMO_ID))
                .thenReturn(List.of());
        stubGuardado();

        SubsidioLiquidacion liq = service.calcular(TRAMO_ID);

        // 30 - 20 = 10 días ESSALUD → 150 * 10 = 1500 (no 0)
        assertThat(liq.getSubsidioEstimado()).isEqualByComparingTo("1500");
        assertThat(liq.getSnapshotJson()).contains("\"diasEntidad\":20", "MATERNIDAD");
    }

    // ── Borde: días previos de enfermedad agotan el cupo de la entidad ────────

    @Test
    void enfermedad_descuenta_dias_entidad_consumidos_en_tramos_previos() {
        SubsidioTramo tramoActual = tramo(30, 0);
        stubCargaComun(SubsidioEstados.TIPO_ENFERMEDAD, tramoActual);
        Map<String, BigDecimal> params = paramsBase();
        params.put("DIAS_ENTIDAD_ENF", new BigDecimal("20"));
        when(parametroResolver.mapaNumerico(eq(FECHA_DESDE), anyInt())).thenReturn(params);
        when(formulaEngine.evaluar(any(), any())).thenReturn(new BigDecimal("150"));
        when(planillaRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(planilla(3000.0)));
        // Tramo previo (abril) consumió 15 de los 20 días que asume la entidad.
        SubsidioTramo tramoPrevio = tramo(15, 0);
        tramoPrevio.setId(50L);
        tramoPrevio.setFechaDesde(LocalDate.of(2026, 4, 1));
        when(tramoRepository.findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(CASO_ID, 1, "S"))
                .thenReturn(List.of(tramoPrevio, tramoActual));
        when(liquidacionRepository.findByTramoIdOrderByVersionLiqDesc(TRAMO_ID))
                .thenReturn(List.of());
        stubGuardado();

        SubsidioLiquidacion liq = service.calcular(TRAMO_ID);

        // Restan 20 - 15 = 5 días de entidad → 30 - 5 = 25 días ESSALUD → 150*25 = 3750
        assertThat(liq.getSubsidioEstimado()).isEqualByComparingTo("3750");
        assertThat(liq.getSnapshotJson()).contains("\"diasEntidad\":5", "\"diasEssalud\":25");
    }

    // ── Error normativo: validación de severidad BLOQUEO detiene el cálculo ───

    @Test
    void validacion_bloqueo_lanza_excepcion_y_no_guarda() {
        when(tramoRepository.findByIdAndActivo(TRAMO_ID, 1)).thenReturn(Optional.of(tramo(30, 0)));
        when(casoRepository.findByIdAndActivo(CASO_ID, 1))
                .thenReturn(Optional.of(caso(SubsidioEstados.TIPO_ENFERMEDAD)));
        when(validacionService.validarCaso(CASO_ID)).thenReturn(List.of(
                new SubsidioValidacionDto("SUB_E001", SubsidioEstados.SEVERIDAD_BLOQUEO,
                        "Falta CITT", CASO_ID, null, null)));

        assertThatThrownBy(() -> service.calcular(TRAMO_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Falta CITT")
                .hasMessageContaining("SUB_E001");

        verify(liquidacionRepository, never()).save(any());
    }

    // ── Error normativo: ya hay liquidación vigente aplicada a planilla ───────

    @Test
    void liquidacion_vigente_aplicada_impide_recalculo() {
        when(tramoRepository.findByIdAndActivo(TRAMO_ID, 1)).thenReturn(Optional.of(tramo(30, 0)));
        when(casoRepository.findByIdAndActivo(CASO_ID, 1))
                .thenReturn(Optional.of(caso(SubsidioEstados.TIPO_ENFERMEDAD)));
        when(validacionService.validarCaso(CASO_ID)).thenReturn(List.of());
        SubsidioLiquidacion aplicada = new SubsidioLiquidacion();
        aplicada.setEstado(SubsidioEstados.LIQ_APLICADO_PLANILLA);
        when(liquidacionRepository.findByTramoIdAndEsVigente(TRAMO_ID, "S"))
                .thenReturn(Optional.of(aplicada));

        assertThatThrownBy(() -> service.calcular(TRAMO_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("SUB_V004");

        verify(liquidacionRepository, never()).save(any());
    }

    // ── Recalculo: desactiva la vigente previa e incrementa la versión ────────

    @Test
    void recalculo_desactiva_vigente_previa_e_incrementa_version() {
        SubsidioTramo tramo = tramo(30, 0);
        when(tramoRepository.findByIdAndActivo(TRAMO_ID, 1)).thenReturn(Optional.of(tramo));
        when(casoRepository.findByIdAndActivo(CASO_ID, 1))
                .thenReturn(Optional.of(caso(SubsidioEstados.TIPO_ENFERMEDAD)));
        when(validacionService.validarCaso(CASO_ID)).thenReturn(List.of());
        SubsidioLiquidacion previa = new SubsidioLiquidacion();
        previa.setEstado(SubsidioEstados.LIQ_CALCULADO); // calculada, no aplicada → recalculable
        when(liquidacionRepository.findByTramoIdAndEsVigente(TRAMO_ID, "S"))
                .thenReturn(Optional.of(previa));
        when(baseHistoricaService.obtenerVigente(CASO_ID)).thenReturn(base());
        when(baseHistoricaService.listarDetalle(BASE_ID)).thenReturn(List.of());
        when(reglaResolver.resolverVigente(FECHA_DESDE)).thenReturn(regla());
        Map<String, BigDecimal> params = paramsBase();
        params.put("DIAS_ENTIDAD_ENF", new BigDecimal("20"));
        when(parametroResolver.mapaNumerico(eq(FECHA_DESDE), anyInt())).thenReturn(params);
        when(formulaRepository.findByReglaVigenciaIdAndCodigoFormulaAndActivo(
                REGLA_ID, "SUBSIDIO_DIARIO_ESSALUD", 1)).thenReturn(Optional.of(formula()));
        when(formulaEngine.evaluar(any(), any())).thenReturn(new BigDecimal("150"));
        when(planillaRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(planilla(3000.0)));
        when(tramoRepository.findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(CASO_ID, 1, "S"))
                .thenReturn(List.of(tramo));
        // Ya existían las versiones 1 y 2 → la nueva debe ser la 3.
        SubsidioLiquidacion v2 = new SubsidioLiquidacion();
        v2.setVersionLiq(2);
        when(liquidacionRepository.findByTramoIdOrderByVersionLiqDesc(TRAMO_ID))
                .thenReturn(List.of(v2));
        stubGuardado();

        SubsidioLiquidacion liq = service.calcular(TRAMO_ID);

        assertThat(liq.getVersionLiq()).isEqualTo(3);
        verify(liquidacionRepository).desactivarVigentesPorTramo(TRAMO_ID);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Stubs comunes a los caminos felices hasta antes del reparto de días. */
    private void stubCargaComun(String tipoCaso, SubsidioTramo tramo) {
        when(tramoRepository.findByIdAndActivo(TRAMO_ID, 1)).thenReturn(Optional.of(tramo));
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso(tipoCaso)));
        when(validacionService.validarCaso(CASO_ID)).thenReturn(List.of());
        when(liquidacionRepository.findByTramoIdAndEsVigente(TRAMO_ID, "S"))
                .thenReturn(Optional.empty());
        when(baseHistoricaService.obtenerVigente(CASO_ID)).thenReturn(base());
        when(baseHistoricaService.listarDetalle(BASE_ID)).thenReturn(List.of());
        when(reglaResolver.resolverVigente(FECHA_DESDE)).thenReturn(regla());
        when(formulaRepository.findByReglaVigenciaIdAndCodigoFormulaAndActivo(
                REGLA_ID, "SUBSIDIO_DIARIO_ESSALUD", 1)).thenReturn(Optional.of(formula()));
    }

    /** Stubs de persistencia: save devuelve la entidad con un id asignado. */
    private void stubGuardado() {
        when(parametroResolver.idsVersionVigente(any())).thenReturn(List.of(5L, 6L));
        when(liquidacionRepository.save(any())).thenAnswer(inv -> {
            SubsidioLiquidacion l = inv.getArgument(0);
            l.setId(200L);
            return l;
        });
        when(tramoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(casoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Map<String, BigDecimal> paramsBase() {
        // Mutable: el servicio hace params.put("DIVISOR_PROMEDIO", ...).
        Map<String, BigDecimal> map = new HashMap<>();
        map.put("TOPE_MENSUAL", new BigDecimal("2475.00"));
        map.put("BIM_PCT_CAS", new BigDecimal("0.45"));
        return map;
    }

    private static SubsidioTramo tramo(int diasSubsidio, int diasLaborados) {
        SubsidioTramo t = new SubsidioTramo();
        t.setId(TRAMO_ID);
        t.setCasoId(CASO_ID);
        t.setPeriodo("202605");
        t.setFechaDesde(FECHA_DESDE);
        t.setFechaHasta(LocalDate.of(2026, 5, 30));
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
        b.setBaseReconocida(new BigDecimal("4950.00"));
        return b;
    }

    private static SubsidioReglaVigencia regla() {
        SubsidioReglaVigencia r = new SubsidioReglaVigencia();
        r.setId(REGLA_ID);
        r.setCodigo("SUB_REGLA");
        r.setVersion("1");
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
