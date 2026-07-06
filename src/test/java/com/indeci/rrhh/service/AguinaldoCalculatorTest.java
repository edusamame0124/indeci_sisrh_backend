package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Track B — Aguinaldo por régimen (SERVIR 100% / CAS %manual con piso / 276 fijo)
 * y afectación tributaria (renta 5ta sí; pensión/EsSalud no).
 */
class AguinaldoCalculatorTest {

    private static final BigDecimal M276 = new BigDecimal("300.00");
    private static final BigDecimal PISO_CAS = new BigDecimal("300.00");

    // ── Punto 2: cálculo por régimen + piso legal CAS ──────────────────────

    @Test
    void servir_gana_100pct_del_sueldo() {
        assertThat(AguinaldoCalculator.calcular("SERVIR", new BigDecimal("18707.14"), null, M276, PISO_CAS))
                .isEqualByComparingTo("18707.14");
    }

    @Test
    void cas_es_porcentaje_manual_por_la_base() {
        assertThat(AguinaldoCalculator.calcular("CAS", new BigDecimal("5364.19"), new BigDecimal("100"), M276, PISO_CAS))
                .isEqualByComparingTo("5364.19");
        assertThat(AguinaldoCalculator.calcular("1057", new BigDecimal("5364.19"), new BigDecimal("50"), M276, PISO_CAS))
                .isEqualByComparingTo("2682.10");
    }

    @Test
    void cas_aplica_piso_legal_300_cuando_el_calculado_es_menor() {
        // base 2000 × 10% = 200 → aplica piso → 300.00
        assertThat(AguinaldoCalculator.calcular("CAS", new BigDecimal("2000.00"), new BigDecimal("10"), M276, PISO_CAS))
                .isEqualByComparingTo("300.00");
    }

    @Test
    void cas_no_aplica_piso_cuando_el_calculado_es_mayor() {
        // base 5364.19 × 10% = 536.42 → no aplica piso
        assertThat(AguinaldoCalculator.calcular("CAS", new BigDecimal("5364.19"), new BigDecimal("10"), M276, PISO_CAS))
                .isEqualByComparingTo("536.42");
    }

    @Test
    void regimen_276_es_monto_fijo_parametrizado() {
        assertThat(AguinaldoCalculator.calcular("276", new BigDecimal("9999.00"), null, M276, PISO_CAS))
                .isEqualByComparingTo("300.00");
    }

    @Test
    void otro_regimen_no_genera_aguinaldo() {
        assertThat(AguinaldoCalculator.calcular("728", new BigDecimal("5000.00"), new BigDecimal("100"), M276, PISO_CAS))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void nulos_devuelven_cero() {
        assertThat(AguinaldoCalculator.calcular(null, new BigDecimal("5000"), new BigDecimal("100"), M276, PISO_CAS))
                .isEqualByComparingTo("0.00");
        assertThat(AguinaldoCalculator.calcular("CAS", null, new BigDecimal("100"), M276, PISO_CAS))
                .isEqualByComparingTo("0.00");
        assertThat(AguinaldoCalculator.calcular("CAS", new BigDecimal("5000"), null, M276, PISO_CAS))
                .isEqualByComparingTo("0.00");
    }

    // ── Punto 3: afectación tributaria (renta 5ta sí; AFP/ONP/EsSalud no) ───

    @Test
    void aguinaldo_afecta_renta_5ta_pero_no_pension_ni_essalud() {
        AguinaldoCalculator.BasesAfectas bases =
                AguinaldoCalculator.basesAfectas(new BigDecimal("5364.19"));

        assertThat(bases.renta5ta()).isEqualByComparingTo("5364.19");
        assertThat(bases.pension()).isEqualByComparingTo("0.00");
        assertThat(bases.essalud()).isEqualByComparingTo("0.00");
    }
}
