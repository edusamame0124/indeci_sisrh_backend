package com.indeci.rrhh.service.asistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Modelo de salida anticipada de dos niveles — mismo contrato que
 * {@link TardanzaDescuentoCalculatorTest}, aplicado a minutos de salida anticipada.
 * Remuneración 1440 / jornada 8 → tasa = 1440 / (30×8×60) = 0.10 por minuto.
 */
class SalidaAnticipadaDescuentoCalculatorTest {

    private static final double REMUN = 1440.0;
    private static final BigDecimal J8 = BigDecimal.valueOf(8);

    @Test
    void dia_mayor_a_umbral_va_a_descuento_1_completo() {
        // 12 min en un día > 10 → Descuento 1 sobre 12 min → 12 × 0.10 = 1.20.
        var r = SalidaAnticipadaDescuentoCalculator.calcular(List.of(12), REMUN, J8, 10, 60);
        assertThat(r.getMinSalidaAnticDiaria()).isEqualTo(12);
        assertThat(r.getMinSalidaAnticMenorAcum()).isZero();
        assertThat(r.getDescuentoDiaria()).isCloseTo(1.20, within(0.001));
        assertThat(r.getDescuentoMensual()).isZero();
        assertThat(r.getDescuentoTotal()).isCloseTo(1.20, within(0.001));
    }

    @Test
    void acumulado_supera_tope_descuenta_el_exceso() {
        // 7 días de 10 = 70 ≤ umbral → acum 70 → exceso 70-60 = 10 → 10 × 0.10 = 1.00.
        var r = SalidaAnticipadaDescuentoCalculator.calcular(
                List.of(10, 10, 10, 10, 10, 10, 10), REMUN, J8, 10, 60);
        assertThat(r.getMinSalidaAnticMenorAcum()).isEqualTo(70);
        assertThat(r.getMinSalidaAnticExcesoMes()).isEqualTo(10);
        assertThat(r.getDescuentoMensual()).isCloseTo(1.00, within(0.001));
    }

    @Test
    void remuneracion_cero_no_descuenta() {
        var r = SalidaAnticipadaDescuentoCalculator.calcular(List.of(40, 80), 0.0, J8, 10, 60);
        assertThat(r.getDescuentoTotal()).isZero();
        // pero la clasificación de minutos sí se calcula:
        assertThat(r.getMinSalidaAnticDiaria()).isEqualTo(120);
    }

    @Test
    void lista_vacia_o_ceros_da_cero() {
        var r = SalidaAnticipadaDescuentoCalculator.calcular(List.of(0, 0), REMUN, J8, 10, 60);
        assertThat(r.getMinSalidaAnticDiaria()).isZero();
        assertThat(r.getMinSalidaAnticMenorAcum()).isZero();
        assertThat(r.getDescuentoTotal()).isZero();
    }
}
