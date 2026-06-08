package com.indeci.rrhh.service.asistencia;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsistenciaCsvParserTest {

    private static final String CABECERA =
            "Día;Fecha;DNI;Nombre;Ent.;Marca1;Marca2;Empresa;Grupo;T/H.Trab;H25%;H35%;H100%;T/H.Ext;Tard.;S/A.t;Observación";

    private final AsistenciaCsvParser parser = new AsistenciaCsvParser();

    @Test
    void parse_ignoraMetadata_y_cabeceraRepetida() {
        String csv = """
                Reporte generado por marcador
                %s
                Lun;01/05/2026;12345678;JUAN PEREZ;08:00;08:05;17:00;INDECI;GR1;08:00;;;;;00:05;;;
                %s
                Mar;02/05/2026;12345678;JUAN PEREZ;08:00;08:00;17:00;INDECI;GR1;08:00;;;;;00:00;;;
                """.formatted(CABECERA, CABECERA);

        AsistenciaCsvParser.ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFilas()).hasSize(2);
        assertThat(result.getFilas().get(0).getFecha()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.getFilas().get(0).getTardanza()).isEqualTo("00:05");
    }

    @Test
    void parse_rechazaSeparadorComa() {
        String csv = "fecha,tipo,minutos\n2026-05-01,LABORAL,0";

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("punto y coma");
    }

    @Test
    void parse_ignoraFilasSinDniValido() {
        String csv = CABECERA + "\n"
                + "Lun;01/05/2026;;SIN DNI;08:00;;;;;;;\n"
                + "Mar;02/05/2026;87654321;ANA LOPEZ;08:00;08:00;17:00;;;;;;;;;";

        AsistenciaCsvParser.ParseResult result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFilas()).hasSize(1);
        assertThat(result.getFilas().get(0).getDni()).isEqualTo("87654321");
    }
}
