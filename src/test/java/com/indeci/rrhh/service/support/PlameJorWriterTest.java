package com.indeci.rrhh.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * B3 / M09 — Tests del PlameJorWriter contra el formato real de INDECI (marzo 2026).
 */
class PlameJorWriterTest {

    @Test
    void serializaMesCompletoComoEnElArchivoReal() {
        // Real: 01|46096439|176|0|0|0|
        String out = PlameJorWriter.write(List.of(
                new PlameJorWriter.Row("46096439", 176, 0, 0, 0)));

        assertThat(out).isEqualTo("01|46096439|176|0|0|0|\r\n");
    }

    @Test
    void noInyectaCodigoDeTipoDeDia() {
        // Regresión v3: el .jor solo lleva horas/minutos; no debe aparecer
        // ningún código de tipo de día (esos van al .snl).
        String out = PlameJorWriter.write(List.of(
                new PlameJorWriter.Row("12345678", 160, 30, 8, 15)));

        assertThat(out).isEqualTo("01|12345678|160|30|8|15|\r\n");
    }

    @Test
    void empleadoSinJornadaSeSerializaEnCero() {
        String out = PlameJorWriter.write(List.of(
                new PlameJorWriter.Row("12345678", 0, 0, 0, 0)));

        assertThat(out).isEqualTo("01|12345678|0|0|0|0|\r\n");
    }

    @Test
    void variasFilasConservanOrden() {
        String out = PlameJorWriter.write(List.of(
                new PlameJorWriter.Row("46096439", 176, 0, 0, 0),
                new PlameJorWriter.Row("29266677", 176, 0, 0, 0)));

        assertThat(out).isEqualTo(
                "01|46096439|176|0|0|0|\r\n"
                + "01|29266677|176|0|0|0|\r\n");
    }

    @Test
    void listaVaciaProduceCadenaVacia() {
        assertThat(PlameJorWriter.write(List.of())).isEmpty();
    }
}
