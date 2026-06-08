package com.indeci.rrhh.service.asistencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsistenciaImportErroresCsvWriterTest {

    private final AsistenciaImportErroresCsvWriter writer =
            new AsistenciaImportErroresCsvWriter(new ObjectMapper());

    @Test
    void generar_incluyeErroresYAdvertenciasConSeparadorInstitucional() {
        AsistenciaImportacion importacion = new AsistenciaImportacion();
        importacion.setId(7L);
        importacion.setPeriodo("2026-06");
        importacion.setNombreArchivo("marcador.csv");

        AsistenciaImportacionFila fila = new AsistenciaImportacionFila();
        fila.setNumeroFila(12);
        fila.setDni("12345678");
        fila.setFecha(LocalDate.of(2026, 6, 5));
        fila.setEstadoFila("ERROR");
        fila.setObservacionMarcador("Marca; incompleta");
        fila.setLineaOriginal("Vie;05/06/2026;12345678");
        fila.setErroresJson("""
                {
                  "errores": ["DNI no encontrado"],
                  "advertencias": ["Nombre distinto al sistema"]
                }
                """);

        String csv = new String(writer.generar(importacion, List.of(fila)), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFFimportacion_id;periodo;archivo;linea;dni;fecha");
        assertThat(csv).contains("7;2026-06;marcador.csv;12;12345678;2026-06-05;ERROR;ERROR;DNI no encontrado");
        assertThat(csv).contains("WARN;Nombre distinto al sistema");
        assertThat(csv).contains("\"Marca; incompleta\"");
    }
}
