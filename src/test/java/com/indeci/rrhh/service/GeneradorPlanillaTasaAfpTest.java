package com.indeci.rrhh.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REGLA-02 — Resolución de tasas AFP: la vigencia oficial es la fuente de verdad y
 * el campo por-empleado solo se respeta como override plausible. Blinda el caso real
 * de la comisión sobre saldo (0.78%) mal digitada que el normalizador convertía en 78%.
 */
class GeneradorPlanillaTasaAfpTest {

    private static final BigDecimal MAX_COMISION = new BigDecimal("0.03");
    private static final BigDecimal MAX_PRIMA = new BigDecimal("0.05");

    @Test
    void descarta_comision_fuera_de_rango_y_usa_la_oficial() {
        // 0.78 (comisión saldo mal digitada) -> normaliza a 0.78 (78%) -> fuera de rango.
        BigDecimal tasa = GeneradorPlanillaService.tasaAfpConOverride(
                0.78, new BigDecimal("1.55"), () -> BigDecimal.ZERO, MAX_COMISION);

        // Debe caer a la oficial de INTEGRA: 1.55% = 0.0155.
        assertThat(tasa).isEqualByComparingTo("0.0155");
    }

    @Test
    void respeta_override_valido_en_rango() {
        // Prima por-empleado 1.37% -> 0.0137, dentro de rango -> se respeta.
        BigDecimal tasa = GeneradorPlanillaService.tasaAfpConOverride(
                1.37, new BigDecimal("1.74"), () -> BigDecimal.ZERO, MAX_PRIMA);

        assertThat(tasa).isEqualByComparingTo("0.0137");
    }

    @Test
    void usa_oficial_cuando_no_hay_valor_por_empleado() {
        BigDecimal tasa = GeneradorPlanillaService.tasaAfpConOverride(
                null, new BigDecimal("1.69"), () -> BigDecimal.ZERO, MAX_COMISION);

        assertThat(tasa).isEqualByComparingTo("0.0169");
    }

    @Test
    void cae_al_fallback_si_no_hay_ni_override_ni_oficial() {
        BigDecimal tasa = GeneradorPlanillaService.tasaAfpConOverride(
                99.0, null, () -> new BigDecimal("0.10"), MAX_COMISION);

        assertThat(tasa).isEqualByComparingTo("0.10");
    }

    @Test
    void mixta_no_cobra_comision_de_flujo_mensual() {
        // Afiliado en esquema MIXTA (comisión sobre saldo): flujo mensual = 0%,
        // aunque tenga tasa por-empleado y exista tasa oficial.
        BigDecimal tasa = GeneradorPlanillaService.tasaComisionFlujo(
                true, 1.55, new BigDecimal("1.55"), () -> BigDecimal.ZERO, MAX_COMISION);

        assertThat(tasa).isEqualByComparingTo("0");
    }

    @Test
    void flujo_puro_si_cobra_comision_de_flujo() {
        BigDecimal tasa = GeneradorPlanillaService.tasaComisionFlujo(
                false, null, new BigDecimal("1.55"), () -> BigDecimal.ZERO, MAX_COMISION);

        assertThat(tasa).isEqualByComparingTo("0.0155");
    }

    @Test
    void normaliza_porcentaje_mayor_que_uno_a_fraccion() {
        assertThat(GeneradorPlanillaService.normalizarTasaEmpleado(13.0))
                .isEqualByComparingTo("0.13");
    }

    @Test
    void pct_de_vigencia_se_convierte_a_fraccion() {
        assertThat(GeneradorPlanillaService.pctAFraccion(new BigDecimal("1.55")))
                .isEqualByComparingTo("0.0155");
    }
}
