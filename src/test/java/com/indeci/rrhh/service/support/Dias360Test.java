package com.indeci.rrhh.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * SPEC_VACACIONES F1 — Dias360 (US/NASD 30/360), equivalente a Excel DAYS360.
 */
class Dias360Test {

    @Test
    void anio_completo_da_360() {
        assertThat(Dias360.entre(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)))
                .isEqualTo(360);
    }

    @Test
    void mismo_dia_da_cero() {
        assertThat(Dias360.entre(LocalDate.of(2023, 5, 10), LocalDate.of(2023, 5, 10)))
                .isEqualTo(0);
    }

    @Test
    void regla_dia_31_inicio_se_ajusta_a_30() {
        // 31-ene → 28-feb: ini 31→30. (2-1)*30 + (28-30) = 30 - 2 = 28
        assertThat(Dias360.entre(LocalDate.of(2020, 1, 31), LocalDate.of(2020, 2, 28)))
                .isEqualTo(28);
    }

    @Test
    void regla_dia_31_ambos_extremos() {
        // 31-ene → 31-mar: ini 31→30, fin 31→30 (pues ini=30). (3-1)*30 = 60
        assertThat(Dias360.entre(LocalDate.of(2020, 1, 31), LocalDate.of(2020, 3, 31)))
                .isEqualTo(60);
    }

    @Test
    void simetrico_negativo_si_fin_antes_de_inicio() {
        LocalDate a = LocalDate.of(2020, 1, 1);
        LocalDate b = LocalDate.of(2020, 3, 1);
        assertThat(Dias360.entre(b, a)).isEqualTo(-Dias360.entre(a, b));
    }

    @Test
    void desglosar_reparte_anios_meses_dias() {
        // 5*360 + 8*30 + 10 = 2050
        Dias360.AniosMesesDias amd = Dias360.desglosar(2050);
        assertThat(amd.anios()).isEqualTo(5);
        assertThat(amd.meses()).isEqualTo(8);
        assertThat(amd.dias()).isEqualTo(10);
    }

    @Test
    void desglosar_cero_da_cero_cero_cero() {
        Dias360.AniosMesesDias amd = Dias360.desglosar(0);
        assertThat(amd.anios()).isZero();
        assertThat(amd.meses()).isZero();
        assertThat(amd.dias()).isZero();
    }

    @Test
    void desglosar_negativo_se_trata_como_cero() {
        Dias360.AniosMesesDias amd = Dias360.desglosar(-15);
        assertThat(amd.anios()).isZero();
        assertThat(amd.meses()).isZero();
        assertThat(amd.dias()).isZero();
    }

    @Test
    void desglosar_menos_de_un_mes_solo_dias() {
        Dias360.AniosMesesDias amd = Dias360.desglosar(25);
        assertThat(amd.anios()).isZero();
        assertThat(amd.meses()).isZero();
        assertThat(amd.dias()).isEqualTo(25);
    }
}
