package com.indeci.rrhh.service.asistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Modelo de tardanzas de dos niveles (Excel institucional INDECI).
 * Remuneración 1440 / jornada 8 → tasa = 1440 / (30×8×60) = 0.10 por minuto.
 */
class TardanzaDescuentoCalculatorTest {

    private static final double REMUN = 1440.0;
    private static final BigDecimal J8 = BigDecimal.valueOf(8);

    @Test
    void dia_mayor_a_umbral_va_a_descuento_1_completo() {
        // 12 min en un día > 10 → Descuento 1 sobre 12 min → 12 × 0.10 = 1.20.
        var r = TardanzaDescuentoCalculator.calcular(List.of(12), REMUN, J8, 10, 60);
        assertThat(r.getMinTardanzaDiaria()).isEqualTo(12);
        assertThat(r.getMinTardanzaMenorAcum()).isZero();
        assertThat(r.getDescuentoDiaria()).isCloseTo(1.20, within(0.001));
        assertThat(r.getDescuentoMensual()).isZero();
        assertThat(r.getDescuentoTotal()).isCloseTo(1.20, within(0.001));
    }

    @Test
    void dia_igual_al_umbral_no_es_descuento_1() {
        // 10 == umbral → NO supera (comparación estricta >) → va al pozo mensual.
        var r = TardanzaDescuentoCalculator.calcular(List.of(10), REMUN, J8, 10, 60);
        assertThat(r.getMinTardanzaDiaria()).isZero();
        assertThat(r.getMinTardanzaMenorAcum()).isEqualTo(10);
    }

    @Test
    void acumulado_menor_al_tope_no_genera_descuento_2() {
        // 8+6+1 = 15 ≤ 60 → exceso 0 → Descuento 2 = 0.
        var r = TardanzaDescuentoCalculator.calcular(List.of(8, 6, 1), REMUN, J8, 10, 60);
        assertThat(r.getMinTardanzaMenorAcum()).isEqualTo(15);
        assertThat(r.getMinTardanzaExcesoMes()).isZero();
        assertThat(r.getDescuentoMensual()).isZero();
    }

    @Test
    void acumulado_supera_tope_descuenta_el_exceso() {
        // 7 días de 10 = 70 ≤ umbral → acum 70 → exceso 70-60 = 10 → 10 × 0.10 = 1.00.
        var r = TardanzaDescuentoCalculator.calcular(
                List.of(10, 10, 10, 10, 10, 10, 10), REMUN, J8, 10, 60);
        assertThat(r.getMinTardanzaMenorAcum()).isEqualTo(70);
        assertThat(r.getMinTardanzaExcesoMes()).isEqualTo(10);
        assertThat(r.getDescuentoMensual()).isCloseTo(1.00, within(0.001));
    }

    @Test
    void mezcla_d1_y_d2() {
        // [12, 8, 6, 31, 5]: >10 → 12+31=43 (D1); ≤10 → 8+6+5=19 (≤60 → exceso 0).
        var r = TardanzaDescuentoCalculator.calcular(List.of(12, 8, 6, 31, 5), REMUN, J8, 10, 60);
        assertThat(r.getMinTardanzaDiaria()).isEqualTo(43);
        assertThat(r.getMinTardanzaMenorAcum()).isEqualTo(19);
        assertThat(r.getMinTardanzaExcesoMes()).isZero();
        assertThat(r.getDescuentoDiaria()).isCloseTo(4.30, within(0.001));
        assertThat(r.getDescuentoTotal()).isCloseTo(4.30, within(0.001));
    }

    @Test
    void tasa_respeta_jornada_distinta_de_8() {
        // Jornada 6 → divisor 30×6×60 = 10800 → tasa = 1440/10800 = 0.13333/min.
        // 12 min D1 → 12 × 0.13333 = 1.60.
        var r = TardanzaDescuentoCalculator.calcular(
                List.of(12), REMUN, BigDecimal.valueOf(6), 10, 60);
        assertThat(r.getDescuentoDiaria()).isCloseTo(1.60, within(0.01));
    }

    @Test
    void remuneracion_cero_no_descuenta() {
        var r = TardanzaDescuentoCalculator.calcular(List.of(40, 80), 0.0, J8, 10, 60);
        assertThat(r.getDescuentoTotal()).isZero();
        // pero la clasificación de minutos sí se calcula:
        assertThat(r.getMinTardanzaDiaria()).isEqualTo(120);
    }

    @Test
    void lista_vacia_o_ceros_da_cero() {
        var r = TardanzaDescuentoCalculator.calcular(List.of(0, 0), REMUN, J8, 10, 60);
        assertThat(r.getMinTardanzaDiaria()).isZero();
        assertThat(r.getMinTardanzaMenorAcum()).isZero();
        assertThat(r.getDescuentoTotal()).isZero();
    }
}
