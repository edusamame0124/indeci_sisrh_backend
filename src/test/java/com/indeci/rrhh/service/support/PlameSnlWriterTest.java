package com.indeci.rrhh.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * B3 / M09 — Tests del PlameSnlWriter (estructura B3 v3; sin golden real de SUNAT).
 */
class PlameSnlWriterTest {

    @Test
    void serializaUnaSuspensionConFormatoEsperado() {
        // Descanso médico (cod 03), 5 días afectos, DNI tipo 1.
        String out = PlameSnlWriter.write(List.of(
                new PlameSnlWriter.Row("1", "12345678", "03", 5)));

        assertThat(out).isEqualTo("1|12345678|03|5|\r\n");
    }

    @Test
    void faltaInjustificadaCod23() {
        String out = PlameSnlWriter.write(List.of(
                new PlameSnlWriter.Row("1", "00256418", "23", 2)));

        assertThat(out).isEqualTo("1|00256418|23|2|\r\n");
    }

    @Test
    void variasSuspensionesConservanOrden() {
        String out = PlameSnlWriter.write(List.of(
                new PlameSnlWriter.Row("1", "12345678", "06", 30),
                new PlameSnlWriter.Row("1", "87654321", "01", 10)));

        assertThat(out).isEqualTo(
                "1|12345678|06|30|\r\n"
                + "1|87654321|01|10|\r\n");
    }

    @Test
    void sinSuspensionesProduceArchivoVacio() {
        // Período sin eventos -> .snl vacío (0 bytes).
        assertThat(PlameSnlWriter.write(List.of())).isEmpty();
    }
}
