package com.indeci.rrhh.service.asistencia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsistenciaEventosReaderTest {

    private final AsistenciaEventosReader reader = new AsistenciaEventosReader();

    private MarcadorCsvRow dia(List<MarcadorCsvRow> filas, String nombre, LocalDate fecha) {
        return filas.stream()
                .filter(f -> f.getNombre().equals(nombre) && fecha.equals(f.getFecha()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró fila " + nombre + " " + fecha));
    }

    @Test
    void leeCoen_agrupaMarcasPorTrabajadorYDia() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("data", "asistencia", "import", "coen_sample.csv"));

        AsistenciaCsvParser.ParseResult result = reader.parse(bytes);
        List<MarcadorCsvRow> filas = result.getFilas();

        // 6 marcas -> 4 dias (2 por trabajador).
        assertThat(filas).hasSize(4);
        assertThat(filas).allSatisfy(f -> {
            assertThat(f.getDni()).isEmpty();          // COEN no trae DNI
            assertThat(f.getFecha()).isNotNull();
        });
    }

    @Test
    void diaConParIngresoSalida_tomaEntradaYSalida() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("data", "asistencia", "import", "coen_sample.csv"));
        List<MarcadorCsvRow> filas = reader.parse(bytes).getFilas();

        MarcadorCsvRow d = dia(filas, "AGUIRRE SAENZ, HUGO RAFAEL", LocalDate.of(2026, 6, 2));
        assertThat(d.getMarca1()).isEqualTo("08:04");   // entrada = primera marca
        assertThat(d.getMarca2()).isEqualTo("17:30");   // salida  = ultima marca
        assertThat(d.getObservacion()).isNull();
    }

    @Test
    void marcaUnicaDeManiana_esSoloIngreso() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("data", "asistencia", "import", "coen_sample.csv"));
        List<MarcadorCsvRow> filas = reader.parse(bytes).getFilas();

        MarcadorCsvRow d = dia(filas, "AGUIRRE SAENZ, HUGO RAFAEL", LocalDate.of(2026, 6, 1));
        assertThat(d.getMarca1()).isEqualTo("07:44");
        assertThat(d.getMarca2()).isNull();
        assertThat(d.getObservacion()).contains("solo ingreso");
    }

    @Test
    void marcaUnicaDeTarde_seClasificaComoSalida_noComoIngresoTardio() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("data", "asistencia", "import", "coen_sample.csv"));
        List<MarcadorCsvRow> filas = reader.parse(bytes).getFilas();

        // Marca única 17:48 etiquetada "Ingreso" por el reloj: NO debe tomarse como
        // entrada (evita tardanza absurda); se clasifica como salida por la hora.
        MarcadorCsvRow d = dia(filas, "ALBINES GARCIA, PERCY", LocalDate.of(2026, 6, 1));
        assertThat(d.getMarca1()).isNull();
        assertThat(d.getMarca2()).isEqualTo("17:48");
        assertThat(d.getObservacion()).contains("solo salida");
    }

    @Test
    void nombreConComaInterna_seParseaComoUnSoloCampo() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("data", "asistencia", "import", "coen_sample.csv"));
        List<MarcadorCsvRow> filas = reader.parse(bytes).getFilas();

        assertThat(filas).extracting(MarcadorCsvRow::getNombre)
                .contains("AGUIRRE SAENZ, HUGO RAFAEL", "ALBINES GARCIA, PERCY");
    }
}
