package com.indeci.rrhh.service.asistencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Component
@RequiredArgsConstructor
public class AsistenciaImportErroresCsvWriter {

    private static final String UTF8_BOM = "\uFEFF";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<String> HEADERS = List.of(
            "importacion_id",
            "periodo",
            "archivo",
            "linea",
            "dni",
            "fecha",
            "estado_fila",
            "severidad",
            "mensaje",
            "observacion_marcador",
            "linea_original");

    private final ObjectMapper objectMapper;

    public byte[] generar(
            AsistenciaImportacion importacion,
            List<AsistenciaImportacionFila> filas) {
        StringBuilder csv = new StringBuilder(UTF8_BOM);
        appendRow(csv, HEADERS);
        filas.stream()
                .flatMap(fila -> toRows(importacion, fila).stream())
                .forEach(row -> appendRow(csv, row));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<List<String>> toRows(
            AsistenciaImportacion importacion,
            AsistenciaImportacionFila fila) {
        Map<String, Object> metadatos = leerMetadatos(fila.getErroresJson());
        List<List<String>> rows = new ArrayList<>();
        leerLista(metadatos, "errores")
                .forEach(mensaje -> rows.add(toRow(importacion, fila, "ERROR", mensaje)));
        leerLista(metadatos, "advertencias")
                .forEach(mensaje -> rows.add(toRow(importacion, fila, "WARN", mensaje)));
        if (rows.isEmpty() && !"VALIDA".equalsIgnoreCase(valor(fila.getEstadoFila()))) {
            rows.add(toRow(importacion, fila, fila.getEstadoFila(), "Fila observada sin detalle registrado."));
        }
        return rows;
    }

    private List<String> toRow(
            AsistenciaImportacion importacion,
            AsistenciaImportacionFila fila,
            String severidad,
            String mensaje) {
        return List.of(
                valor(importacion.getId()),
                valor(importacion.getPeriodo()),
                valor(importacion.getNombreArchivo()),
                valor(fila.getNumeroFila()),
                valor(fila.getDni()),
                fila.getFecha() != null ? DATE_FORMAT.format(fila.getFecha()) : "",
                valor(fila.getEstadoFila()),
                valor(severidad),
                valor(mensaje),
                valor(fila.getObservacionMarcador()),
                valor(fila.getLineaOriginal()));
    }

    private Map<String, Object> leerMetadatos(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private List<String> leerLista(Map<String, Object> metadatos, String clave) {
        Object valor = metadatos.get(clave);
        if (valor instanceof List<?> lista) {
            return lista.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private void appendRow(StringBuilder csv, List<String> values) {
        StringJoiner joiner = new StringJoiner(";");
        values.stream().map(this::escape).forEach(joiner::add);
        csv.append(joiner).append(System.lineSeparator());
    }

    private String escape(String value) {
        String safe = valor(value);
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String valor(Object value) {
        return value != null ? value.toString() : "";
    }
}
