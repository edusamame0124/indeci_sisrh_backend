package com.indeci.rrhh.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V012_03 — Guard de rango [0, 31] de los días laborados antes de persistir.
 * Un valor fuera de rango se descarta a favor de 30 (default).
 */
class GeneradorPlanillaDiasLaboradosTest {

    @Test
    void valor_en_rango_se_conserva() {
        // 21 = 30 − 9 faltas (caso BALTAZAR).
        assertThat(GeneradorPlanillaService.diasLaboradosSeguro(21, 1L, "202606")).isEqualTo(21);
    }

    @Test
    void limite_inferior_cero_es_valido() {
        assertThat(GeneradorPlanillaService.diasLaboradosSeguro(0, 1L, "202606")).isZero();
    }

    @Test
    void limite_superior_31_es_valido() {
        assertThat(GeneradorPlanillaService.diasLaboradosSeguro(31, 1L, "202606")).isEqualTo(31);
    }

    @Test
    void negativo_cae_a_30() {
        assertThat(GeneradorPlanillaService.diasLaboradosSeguro(-1, 1L, "202606")).isEqualTo(30);
    }

    @Test
    void mayor_a_31_cae_a_30() {
        assertThat(GeneradorPlanillaService.diasLaboradosSeguro(32, 1L, "202606")).isEqualTo(30);
    }
}
