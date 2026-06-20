package com.indeci.rrhh.service.asistencia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsistenciaCsvParserTest {

    private AsistenciaCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new AsistenciaCsvParser();
    }

    @Test
    void parse_fechaFlexible_dSlashM_yyyy() {
        String csv = cabeceraMarcador() + "Lun;1/06/2026;8274536;LOPEZ BENITES ANA MELVA;08:30;17:00;07:29;17:03\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFilas()).hasSize(1);
        assertThat(result.getFilas().get(0).getFecha()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void parse_fechaEstandar_ddSlashMMyyyy() {
        String csv = cabeceraMarcador() + "Lun;01/06/2026;8274536;LOPEZ BENITES ANA MELVA;08:30;17:00;07:29;17:03\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFilas()).hasSize(1);
        assertThat(result.getFilas().get(0).getFecha()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void parse_fechaConGuiones_ddMMyyyy() {
        String csv = cabeceraMarcador() + "Lun;01-06-2026;8274536;LOPEZ BENITES ANA MELVA;08:30;17:00;07:29;17:03\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFilas()).hasSize(1);
        assertThat(result.getFilas().get(0).getFecha()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void parse_dniSinCeroInicial_normalizaA8Digitos() {
        String csv = cabeceraMarcador() + "Lun;10/06/2026;8274536;LOPEZ BENITES ANA MELVA;08:30;17:00;07:29;17:03\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFilas()).hasSize(1);
        assertThat(result.getFilas().get(0).getDni()).isEqualTo("08274536");
    }

    @Test
    void parse_dniConCeroInicial_conserva8Digitos() {
        String csv = cabeceraMarcador() + "Lun;10/06/2026;08274536;LOPEZ BENITES ANA MELVA;08:30;17:00;07:29;17:03\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFilas()).hasSize(1);
        assertThat(result.getFilas().get(0).getDni()).isEqualTo("08274536");
    }

    @Test
    void parse_csvBiometricoReal_todasLasFilasConFechaYNormalizaDni() throws IOException {
        Path csvPath = Path.of("data", "asistencia", "import", "22_Reporte.csv");
        byte[] bytes = Files.readAllBytes(csvPath);

        var result = parser.parse(bytes);

        assertThat(result.getFilas()).isNotEmpty();
        assertThat(result.getFilas()).allSatisfy(row -> {
            assertThat(row.getFecha()).as("fecha fila %d", row.getNumeroFila()).isNotNull();
            assertThat(row.getDni()).hasSize(8);
        });
        assertThat(result.getFilas().get(0).getDni()).isEqualTo("08274536");
        assertThat(result.getFilas().get(0).getFecha()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.getFilas()).hasSize(10);
    }

    @Test
    void parse_separadorComa_rechazaArchivo() {
        String csv = "DIA,FECHA,DNI,NOMBRE,ENT.,SAL.,MARCA1,MARCA2\n"
                + "Lun,01/06/2026,8274536,LOPEZ BENITES ANA MELVA,08:30,17:00,07:29,17:03\n";

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("punto y coma");
    }

    @Test
    void parse_sinCabeceraMarcador_rechazaArchivo() {
        String csv = "dato1;dato2;dato3\nvalor1;valor2;valor3\n";

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cabecera del marcador");
    }

    @Test
    void parse_dniInvalido_descartaFila() {
        String csv = cabeceraMarcador()
                + "Lun;10/06/2026;ABC;LOPEZ BENITES ANA MELVA;08:30;17:00;07:29;17:03\n"
                + "Lun;10/06/2026;8274536;LOPEZ BENITES ANA MELVA;08:30;17:00;07:29;17:03\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFilas()).hasSize(1);
        assertThat(result.getFilas().get(0).getDni()).isEqualTo("08274536");
    }

    @Test
    void parse_bomUtf8_detectaCabeceraYParseaFila() {
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = (cabeceraMarcador() + "Lun;2/06/2026;8274536;LOPEZ BENITES ANA MELVA;08:30;17:00;07:29;17:03\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] csv = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, csv, 0, bom.length);
        System.arraycopy(body, 0, csv, bom.length, body.length);

        var result = parser.parse(csv);

        assertThat(result.getFilas()).hasSize(1);
        assertThat(result.getFilas().get(0).getFecha()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(result.getFilas().get(0).getDni()).isEqualTo("08274536");
    }

    private static String cabeceraMarcador() {
        return "DÍA;FECHA;DNI;NOMBRE;ENT.;SAL.;MARCA1;MARCA2\n";
    }
}
