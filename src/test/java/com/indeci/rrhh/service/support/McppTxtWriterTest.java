package com.indeci.rrhh.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * B3 / M14 — Tests del McppTxtWriter contra los PLL*.TXT reales de INDECI (marzo 2026).
 */
class McppTxtWriterTest {

    private McppTxtWriter.Header header() {
        // Real: 000009|2026|04|01|03|0038|96|114337.16|20690.95|0.00
        return new McppTxtWriter.Header(
                "000009", 2026, 4, "01", "03", 38, 96,
                new BigDecimal("114337.16"), new BigDecimal("20690.95"), BigDecimal.ZERO);
    }

    @Test
    void cabeceraConFormatoExacto() {
        String out = McppTxtWriter.write(header(), List.of());

        assertThat(out).isEqualTo("000009|2026|04|01|03|0038|96|114337.16|20690.95|0.00\r\n");
    }

    @Test
    void detalleIngresoConFormatoExacto() {
        // Real: 2|02835030|00|1|0131|DS 279-2024-EF|2764.19|4|000511
        McppTxtWriter.Header h = new McppTxtWriter.Header(
                "000009", 2026, 4, "01", "03", 38, 1,
                new BigDecimal("2764.19"), BigDecimal.ZERO, BigDecimal.ZERO);

        String out = McppTxtWriter.write(h, List.of(
                new McppTxtWriter.Detail("02835030", "1", "0131",
                        "DS 279-2024-EF", new BigDecimal("2764.19"), "4", "000511")));

        assertThat(out).isEqualTo(
                "000009|2026|04|01|03|0038|1|2764.19|0.00|0.00\r\n"
                + "2|02835030|00|1|0131|DS 279-2024-EF|2764.19|4|000511\r\n");
    }

    @Test
    void detalleDescuentoCuotaSindical() {
        // Real: 2|07636270|00|2|0067|CUOTA SINDICAL CAS|10.00|4|000977
        McppTxtWriter.Header h = new McppTxtWriter.Header(
                "000009", 2026, 4, "01", "03", 38, 1,
                BigDecimal.ZERO, new BigDecimal("10.00"), BigDecimal.ZERO);

        String out = McppTxtWriter.write(h, List.of(
                new McppTxtWriter.Detail("07636270", "2", "0067",
                        "CUOTA SINDICAL CAS", new BigDecimal("10.00"), "4", "000977")));

        assertThat(out).contains(
                "2|07636270|00|2|0067|CUOTA SINDICAL CAS|10.00|4|000977\r\n");
    }

    @Test
    void mesYnroPlanillaSeZeroPadean() {
        McppTxtWriter.Header h = new McppTxtWriter.Header(
                "000009", 2026, 1, "01", "01", 7, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        String out = McppTxtWriter.write(h, List.of());

        // mes 1 -> "01", nroPlanilla 7 -> "0007"
        assertThat(out).isEqualTo("000009|2026|01|01|01|0007|0|0.00|0.00|0.00\r\n");
    }

    @Test
    void montoConservaPrecisionYNoUsaQuirk00punto00() {
        // MCPP usa "0.00" para cero (a diferencia del .rem que usa "00.00").
        McppTxtWriter.Header h = new McppTxtWriter.Header(
                "000009", 2026, 4, "01", "03", 38, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        String out = McppTxtWriter.write(h, List.of(
                new McppTxtWriter.Detail("12345678", "1", "0131",
                        "HONORARIOS CAS", BigDecimal.ZERO, "4", "000001")));

        assertThat(out).contains("|HONORARIOS CAS|0.00|4|000001\r\n");
    }

    @Test
    void variosDetallesConservanOrden() {
        String out = McppTxtWriter.write(header(), List.of(
                new McppTxtWriter.Detail("02835030", "1", "0131",
                        "DS 279-2024-EF", new BigDecimal("2764.19"), "4", "000511"),
                new McppTxtWriter.Detail("02835030", "2", "0009",
                        "AP.OBLIG.CAS", new BigDecimal("286.42"), "4", "000511")));

        assertThat(out).isEqualTo(
                "000009|2026|04|01|03|0038|96|114337.16|20690.95|0.00\r\n"
                + "2|02835030|00|1|0131|DS 279-2024-EF|2764.19|4|000511\r\n"
                + "2|02835030|00|2|0009|AP.OBLIG.CAS|286.42|4|000511\r\n");
    }
}
