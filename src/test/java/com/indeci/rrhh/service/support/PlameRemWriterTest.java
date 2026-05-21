package com.indeci.rrhh.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * B3 / M09 — Tests del PlameRemWriter contra el formato real de INDECI (marzo 2026).
 */
class PlameRemWriterTest {

    @Test
    void serializaUnaLineaConFormatoExacto() {
        // Cross-ref real: 01|00256418|0608|236.42|236.42|
        String out = PlameRemWriter.write(List.of(
                new PlameRemWriter.Row("00256418", "0608",
                        new BigDecimal("236.42"), new BigDecimal("236.42"))));

        assertThat(out).isEqualTo("01|00256418|0608|236.42|236.42|\r\n");
    }

    @Test
    void elCeroSeEscribeComo00punto00() {
        // Cross-ref real: 01|00256418|0601|00.00|00.00|
        String out = PlameRemWriter.write(List.of(
                new PlameRemWriter.Row("00256418", "0601",
                        BigDecimal.ZERO, BigDecimal.ZERO)));

        assertThat(out).isEqualTo("01|00256418|0601|00.00|00.00|\r\n");
    }

    @Test
    void montoDeUnDigitoEnteroNoSePadea() {
        // Verificado en el .rem real: existe |1.54| sin padding (no "01.54").
        String out = PlameRemWriter.write(List.of(
                new PlameRemWriter.Row("12345678", "0606",
                        new BigDecimal("1.54"), new BigDecimal("1.54"))));

        assertThat(out).isEqualTo("01|12345678|0606|1.54|1.54|\r\n");
    }

    @Test
    void montoGrandeConservaTodosLosDigitos() {
        String out = PlameRemWriter.write(List.of(
                new PlameRemWriter.Row("12345678", "0601",
                        new BigDecimal("2000.00"), new BigDecimal("2000.00"))));

        assertThat(out).isEqualTo("01|12345678|0601|2000.00|2000.00|\r\n");
    }

    @Test
    void redondeaAHalfUpAEscala2() {
        String out = PlameRemWriter.write(List.of(
                new PlameRemWriter.Row("12345678", "0618",
                        new BigDecimal("3580.245"), new BigDecimal("3580.245"))));

        // 3580.245 -> 3580.25 (HALF_UP)
        assertThat(out).isEqualTo("01|12345678|0618|3580.25|3580.25|\r\n");
    }

    @Test
    void devengadoYpagadoPuedenDiferir() {
        String out = PlameRemWriter.write(List.of(
                new PlameRemWriter.Row("12345678", "0601",
                        new BigDecimal("100.00"), BigDecimal.ZERO)));

        assertThat(out).isEqualTo("01|12345678|0601|100.00|00.00|\r\n");
    }

    @Test
    void variasLineasConservanOrden() {
        String out = PlameRemWriter.write(List.of(
                new PlameRemWriter.Row("00256418", "0601", BigDecimal.ZERO, BigDecimal.ZERO),
                new PlameRemWriter.Row("00256418", "0606",
                        new BigDecimal("32.39"), new BigDecimal("32.39"))));

        assertThat(out).isEqualTo(
                "01|00256418|0601|00.00|00.00|\r\n"
                + "01|00256418|0606|32.39|32.39|\r\n");
    }

    @Test
    void listaVaciaProduceCadenaVacia() {
        assertThat(PlameRemWriter.write(List.of())).isEmpty();
    }
}
