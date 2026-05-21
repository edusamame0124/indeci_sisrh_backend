package com.indeci.rrhh.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * B3 / M09 — Tests de consolidación BigDecimal por DNI (decisión D2 + v3 corrección 1).
 */
class PlameConsolidatorTest {

    @Test
    void unConceptoProduceUnaFila() {
        List<PlameRemWriter.Row> out = PlameConsolidator.consolidar(List.of(
                new PlameConsolidator.RawConcepto("00256418", "0601", new BigDecimal("2264.19"))));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).dni()).isEqualTo("00256418");
        assertThat(out.get(0).codigoPlame()).isEqualTo("0601");
        assertThat(out.get(0).devengado()).isEqualByComparingTo("2264.19");
        assertThat(out.get(0).pagado()).isEqualByComparingTo("2264.19");
    }

    @Test
    void mismoDniMismoCodigoSeSuma() {
        // Planilla regular + vacaciones truncas del mismo DNI/código → una sola línea.
        List<PlameRemWriter.Row> out = PlameConsolidator.consolidar(List.of(
                new PlameConsolidator.RawConcepto("00256418", "0601", new BigDecimal("2000.00")),
                new PlameConsolidator.RawConcepto("00256418", "0601", new BigDecimal("264.19"))));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).devengado()).isEqualByComparingTo("2264.19");
    }

    @Test
    void sumaPreservaPrecisionSinRedondearAntesDeTiempo() {
        // 1234.567 + 2345.678 = 3580.245 EXACTO (sin redondeo intermedio).
        List<PlameRemWriter.Row> out = PlameConsolidator.consolidar(List.of(
                new PlameConsolidator.RawConcepto("12345678", "0618", new BigDecimal("1234.567")),
                new PlameConsolidator.RawConcepto("12345678", "0618", new BigDecimal("2345.678"))));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).devengado()).isEqualByComparingTo("3580.245");
    }

    @Test
    void consolidacionMasWriterAplicaHalfUpAlSerializar() {
        // 3580.245 consolidado → el writer redondea a 3580.25 (HALF_UP).
        List<PlameRemWriter.Row> out = PlameConsolidator.consolidar(List.of(
                new PlameConsolidator.RawConcepto("12345678", "0618", new BigDecimal("1234.567")),
                new PlameConsolidator.RawConcepto("12345678", "0618", new BigDecimal("2345.678"))));

        String rem = PlameRemWriter.write(out);
        assertThat(rem).isEqualTo("01|12345678|0618|3580.25|3580.25|\r\n");
    }

    @Test
    void tresCentavosMediosSeSumanYRedondeanHalfUp() {
        // 0.005 + 0.005 + 0.005 = 0.015 (exacto) → writer 0.02 (HALF_UP).
        List<PlameRemWriter.Row> out = PlameConsolidator.consolidar(List.of(
                new PlameConsolidator.RawConcepto("12345678", "2039", new BigDecimal("0.005")),
                new PlameConsolidator.RawConcepto("12345678", "2039", new BigDecimal("0.005")),
                new PlameConsolidator.RawConcepto("12345678", "2039", new BigDecimal("0.005"))));

        assertThat(out.get(0).devengado()).isEqualByComparingTo("0.015");
        assertThat(PlameRemWriter.write(out)).isEqualTo("01|12345678|2039|0.02|0.02|\r\n");
    }

    @Test
    void distintosCodigosDelMismoDniSonFilasSeparadasOrdenadas() {
        List<PlameRemWriter.Row> out = PlameConsolidator.consolidar(List.of(
                new PlameConsolidator.RawConcepto("00256418", "0608", new BigDecimal("236.42")),
                new PlameConsolidator.RawConcepto("00256418", "0601", new BigDecimal("2264.19"))));

        assertThat(out).hasSize(2);
        // Orden por código asc: 0601 antes que 0608.
        assertThat(out.get(0).codigoPlame()).isEqualTo("0601");
        assertThat(out.get(1).codigoPlame()).isEqualTo("0608");
    }

    @Test
    void distintosDniSeOrdenanAscendente() {
        List<PlameRemWriter.Row> out = PlameConsolidator.consolidar(List.of(
                new PlameConsolidator.RawConcepto("70986067", "0601", new BigDecimal("100.00")),
                new PlameConsolidator.RawConcepto("00256418", "0601", new BigDecimal("200.00"))));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).dni()).isEqualTo("00256418");
        assertThat(out.get(1).dni()).isEqualTo("70986067");
    }

    @Test
    void listaVaciaProduceListaVacia() {
        assertThat(PlameConsolidator.consolidar(List.of())).isEmpty();
    }
}
