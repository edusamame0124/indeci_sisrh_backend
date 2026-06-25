package com.indeci.rrhh.service.subsidio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.dto.subsidio.SubsidioBaseDetalleInput;
import com.indeci.rrhh.dto.subsidio.SubsidioBaseManualInput;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.SubsidioBaseDetalle;
import com.indeci.rrhh.entity.SubsidioBaseHistorica;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioReglaVigencia;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.SubsidioBaseDetalleRepository;
import com.indeci.rrhh.repository.SubsidioBaseHistoricaRepository;
import com.indeci.rrhh.repository.SubsidioCasoRepository;

@ExtendWith(MockitoExtension.class)
class SubsidioBaseHistoricaServiceTest {

    private static final Long CASO_ID = 10L;
    private static final Long EMPLEADO_ID = 41L;

    @Mock private SubsidioCasoRepository casoRepository;
    @Mock private SubsidioBaseHistoricaRepository baseRepository;
    @Mock private SubsidioBaseDetalleRepository detalleRepository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private SubsidioParametroResolverService parametroResolver;
    @Mock private SubsidioReglaResolverService reglaResolver;
    @Mock private EmpleadoPlanillaRepository planillaRepository;

    private SubsidioBaseHistoricaService service;

    @BeforeEach
    void setUp() {
        service = new SubsidioBaseHistoricaService(
                casoRepository,
                baseRepository,
                detalleRepository,
                movimientoRepository,
                parametroResolver,
                reglaResolver,
                planillaRepository,
                new ObjectMapper());
    }

    @Test
    void calcula_base_desde_12_meses_planilla_topados() {
        SubsidioCaso caso = caso();
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), anyInt())).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"),
                "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(mov("2025-05", 3000.0), mov("2025-06", 5000.0)));
        when(baseRepository.save(any())).thenAnswer(inv -> {
            SubsidioBaseHistorica b = inv.getArgument(0);
            b.setId(99L);
            return b;
        });

        SubsidioBaseHistorica base = service.calcular(CASO_ID);

        assertThat(base.getBaseReconocida()).isEqualByComparingTo("4950.00");
        assertThat(base.getFuente()).isEqualTo("PLANILLA");
        assertThat(base.getMesesEvaluados()).isEqualTo(2);
    }

    @Test
    void afiliacion_corta_usa_fallback_uniforme() {
        SubsidioCaso caso = caso();
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), anyInt())).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"),
                "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of());
        when(planillaRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of());
        when(baseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubsidioBaseHistorica base = service.calcular(CASO_ID);

        assertThat(base.getFuente()).isEqualTo("PARAMETRO");
        assertThat(base.getMesesEvaluados()).isEqualTo(12);
    }

    /**
     * Regresión P1 (Opción A): sin historial de planilla, el respaldo PARAMETRO
     * debe persistir 12 detalles topados; de lo contrario el SUM_TOP de la fórmula
     * del subsidio diario sumaría una lista vacía y devolvería 0 (bug S/ 0.00).
     */
    @Test
    void fallback_uniforme_genera_doce_detalles_topados() {
        SubsidioCaso caso = caso();
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), anyInt())).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"),
                "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of());
        // Sueldo CAS 3864.19 > tope 2475 → cada mes topa en 2475.
        when(planillaRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(planilla(3864.19)));
        when(baseRepository.save(any())).thenAnswer(inv -> {
            SubsidioBaseHistorica b = inv.getArgument(0);
            b.setId(77L);
            return b;
        });

        SubsidioBaseHistorica base = service.calcular(CASO_ID);

        assertThat(base.getFuente()).isEqualTo("PARAMETRO");
        assertThat(base.getMesesEvaluados()).isEqualTo(12);
        assertThat(base.getBaseReconocida()).isEqualByComparingTo("29700.00"); // 2475 × 12

        ArgumentCaptor<SubsidioBaseDetalle> captor =
                ArgumentCaptor.forClass(SubsidioBaseDetalle.class);
        verify(detalleRepository, times(12)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(d -> {
                    assertThat(d.getBaseComputable()).isEqualByComparingTo("2475");
                    assertThat(d.getBaseHistoricaId()).isEqualTo(77L);
                });
    }

    /**
     * Regresión P2: cada mes de la base se topa con el tope BIM de SU propio año
     * (2025 → 2407.50; 2026 → 2475), no con el tope del año de la contingencia.
     */
    @Test
    void base_topa_cada_mes_con_el_tope_de_su_anio() {
        SubsidioCaso caso = caso(); // contingencia mayo 2026
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), eq(2026))).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"), "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.mapaNumerico(any(), eq(2025))).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2407.50"), "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());
        // Sueldos por encima de ambos topes → cada mes topa en el de su año.
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(mov("2025-12", 9000.0), mov("2026-01", 9000.0)));
        when(baseRepository.save(any())).thenAnswer(inv -> {
            SubsidioBaseHistorica b = inv.getArgument(0);
            b.setId(88L);
            return b;
        });

        SubsidioBaseHistorica base = service.calcular(CASO_ID);

        assertThat(base.getFuente()).isEqualTo("PLANILLA");
        // 2407.50 (dic-25) + 2475.00 (ene-26) = 4882.50
        assertThat(base.getBaseReconocida()).isEqualByComparingTo("4882.50");

        ArgumentCaptor<SubsidioBaseDetalle> captor =
                ArgumentCaptor.forClass(SubsidioBaseDetalle.class);
        verify(detalleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(d -> d.getTopeAplicado().stripTrailingZeros())
                .containsExactlyInAnyOrder(
                        new BigDecimal("2407.5"), new BigDecimal("2475"));
    }

    // ── Base manual / MIXTA ───────────────────────────────────────────────────

    /**
     * Caso VARGAS del Excel con base MANUAL: 12 meses (4 topados 2025, set-25 LSGR
     * parcial 964.29, oct/nov/dic-25 LSGR 0, 4 topados 2026) → base 20 494.29,
     * divisor 360 (los meses 0 siguen contando). Diario = 20494.29/360 = 56.93.
     */
    @Test
    void guardar_manual_reproduce_base_vargas_del_excel() {
        SubsidioCaso caso = caso(); // contingencia mayo 2026
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), eq(2026))).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"), "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.mapaNumerico(any(), eq(2025))).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2407.50"), "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(parametroResolver.obtenerTexto(eq("SUBSIDIO_DIVISOR_MODO"), any())).thenReturn("FIJO_360");
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());
        when(baseRepository.save(any())).thenAnswer(inv -> {
            SubsidioBaseHistorica b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });

        SubsidioBaseManualInput input = new SubsidioBaseManualInput(List.of(
                detIn("2025-05", "3000", "NORMAL"), detIn("2025-06", "3000", "NORMAL"),
                detIn("2025-07", "3000", "NORMAL"), detIn("2025-08", "3000", "NORMAL"),
                detIn("2025-09", "964.29", "LSGR"),
                detIn("2025-10", "0", "LSGR"), detIn("2025-11", "0", "LSGR"),
                detIn("2025-12", "0", "LSGR"),
                detIn("2026-01", "3000", "NORMAL"), detIn("2026-02", "3000", "NORMAL"),
                detIn("2026-03", "3000", "NORMAL"), detIn("2026-04", "3000", "NORMAL")),
                "Carga inicial desde Excel LD");

        SubsidioBaseHistorica base = service.guardarManual(CASO_ID, input);

        assertThat(base.getFuente()).isEqualTo("MANUAL");
        assertThat(base.getMesesEvaluados()).isEqualTo(12);
        assertThat(base.getDivisorPromedio()).isEqualTo(360);
        // 4×2407.50 + 964.29 + 0×3 + 4×2475 = 20 494.29
        assertThat(base.getBaseReconocida()).isEqualByComparingTo("20494.29");

        ArgumentCaptor<SubsidioBaseDetalle> captor =
                ArgumentCaptor.forClass(SubsidioBaseDetalle.class);
        verify(detalleRepository, times(12)).save(captor.capture());
        SubsidioBaseDetalle set25 = porPeriodo(captor, "202509");
        assertThat(set25.getBaseComputable()).isEqualByComparingTo("964.29"); // LSGR parcial conservado
        assertThat(set25.getEsManual()).isEqualTo("S");
        assertThat(porPeriodo(captor, "202510").getBaseComputable())
                .isEqualByComparingTo("0"); // LSGR total
    }

    @Test
    void guardar_manual_divisor_proporcional_para_afiliacion_corta() {
        SubsidioCaso caso = caso();
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), anyInt())).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"), "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(parametroResolver.obtenerTexto(eq("SUBSIDIO_DIVISOR_MODO"), any()))
                .thenReturn("PROPORCIONAL");
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());
        when(baseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubsidioBaseManualInput input = new SubsidioBaseManualInput(List.of(
                detIn("2026-01", "3000", "NORMAL"), detIn("2026-02", "3000", "NORMAL"),
                detIn("2026-03", "3000", "NORMAL"), detIn("2026-04", "3000", "NORMAL"),
                detIn("2026-05", "3000", "NORMAL"), detIn("2026-06", "3000", "NORMAL")),
                "Solo 6 meses de afiliación");

        SubsidioBaseHistorica base = service.guardarManual(CASO_ID, input);

        assertThat(base.getMesesEvaluados()).isEqualTo(6);
        assertThat(base.getDivisorPromedio()).isEqualTo(180); // 6 × 30
        assertThat(base.getBaseReconocida()).isEqualByComparingTo("14850.00"); // 6 × 2475
    }

    @Test
    void guardar_manual_periodo_duplicado_lanza_excepcion() {
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso()));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), anyInt())).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"), "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(parametroResolver.obtenerTexto(eq("SUBSIDIO_DIVISOR_MODO"), any())).thenReturn("FIJO_360");
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());

        SubsidioBaseManualInput input = new SubsidioBaseManualInput(List.of(
                detIn("2026-01", "3000", "NORMAL"), detIn("2026-01", "3000", "NORMAL")), null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.guardarManual(CASO_ID, input))
                .isInstanceOf(com.indeci.exception.NegocioException.class)
                .hasMessageContaining("duplicado");
    }

    @Test
    void preparar_borrador_precarga_movimientos_y_deja_blanco_lo_faltante() {
        SubsidioCaso caso = caso(); // contingencia mayo 2026 → ventana may25..abr26
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(parametroResolver.mapaNumerico(any(), eq(2026))).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"), "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.mapaNumerico(any(), eq(2025))).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2407.50"), "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(mov("2026-01", 3000.0)));

        SubsidioBaseHistoricaService.BaseHistoricaPreview preview = service.prepararBorrador(CASO_ID);

        assertThat(preview.detalles()).hasSize(12);
        assertThat(preview.base().getFuente()).isEqualTo("MIXTA");
        SubsidioBaseDetalle ene26 = preview.detalles().stream()
                .filter(d -> d.getPeriodo().equals("202601")).findFirst().orElseThrow();
        assertThat(ene26.getEsManual()).isEqualTo("N");
        assertThat(ene26.getFuenteMovimientoId()).isNotNull();
        assertThat(ene26.getBaseComputable()).isEqualByComparingTo("2475.00"); // topar(3000, 2475)
        SubsidioBaseDetalle feb26 = preview.detalles().stream()
                .filter(d -> d.getPeriodo().equals("202602")).findFirst().orElseThrow();
        assertThat(feb26.getEsManual()).isEqualTo("S"); // en blanco
        assertThat(feb26.getRemuneracionReal()).isEqualByComparingTo("0");
    }

    private static SubsidioBaseDetalleInput detIn(String periodo, String real, String incidencia) {
        return new SubsidioBaseDetalleInput(periodo, new BigDecimal(real), incidencia, null, null);
    }

    private static SubsidioBaseDetalle porPeriodo(
            ArgumentCaptor<SubsidioBaseDetalle> captor, String periodo) {
        return captor.getAllValues().stream()
                .filter(d -> periodo.equals(d.getPeriodo()))
                .findFirst().orElseThrow();
    }

    private static EmpleadoPlanilla planilla(double sueldoBasico) {
        EmpleadoPlanilla p = new EmpleadoPlanilla();
        p.setEmpleadoId(EMPLEADO_ID);
        p.setSueldoBasico(sueldoBasico);
        p.setActivo(1);
        return p;
    }

    private static SubsidioCaso caso() {
        SubsidioCaso c = new SubsidioCaso();
        c.setId(CASO_ID);
        c.setEmpleadoId(EMPLEADO_ID);
        c.setFechaInicio(LocalDate.of(2026, 5, 1));
        c.setFechaContingencia(LocalDate.of(2026, 5, 1));
        return c;
    }

    private static SubsidioReglaVigencia regla() {
        SubsidioReglaVigencia r = new SubsidioReglaVigencia();
        r.setId(1L);
        return r;
    }

    private static MovimientoPlanilla mov(String periodo, double ingresos) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(1L);
        m.setPeriodo(periodo);
        m.setTotalIngresos(ingresos);
        return m;
    }
}
