package com.indeci.rrhh.service.asistencia;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AsistenciaCsvParser {

    /**
     * Formatos del biométrico INDECI: día sin cero ({@code 1/06/2026}) y variantes con guiones.
     */
    private static final DateTimeFormatter[] FECHA_MARCADOR = {
            fmt("d/MM/uuuu"),
            fmt("dd/MM/uuuu"),
            fmt("d/M/uuuu"),
            fmt("d-M-uuuu"),
            fmt("dd-MM-uuuu"),
    };

    private static DateTimeFormatter fmt(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
                .withResolverStyle(ResolverStyle.SMART);
    }

    public ParseResult parse(byte[] bytes) {
        String texto = MarcadorEncoding.decodificar(bytes);
        validarSeparador(texto);
        String[] lineas = texto.split("\\r?\\n");
        int headerIndex = detectarCabecera(lineas);
        if (headerIndex < 0) {
            throw new IllegalArgumentException(
                    "No se encontró la cabecera del marcador en el archivo CSV.");
        }

        String lineaCabecera = lineas[headerIndex].trim();
        String[] headers = splitSemicolon(lineaCabecera);
        List<MarcadorCsvRow> filas = new ArrayList<>();
        for (int i = headerIndex + 1; i < lineas.length; i++) {
            String cruda = lineas[i].trim();
            if (cruda.isEmpty() || esCabeceraRepetida(cruda, lineaCabecera)) {
                continue;
            }
            if (!cruda.contains(";")) {
                continue;
            }
            MarcadorCsvRow row = parseFila(cruda, i + 1, headers);
            if (tieneDniValido(row)) {
                filas.add(row);
            }
        }

        ParseResult result = new ParseResult();
        result.setEncoding(MarcadorEncoding.detectarEncoding(bytes));
        result.setFilas(filas);
        return result;
    }

    private MarcadorCsvRow parseFila(String cruda, int numeroFila, String[] headers) {
        String[] valores = splitSemicolon(cruda);
        MarcadorCsvRow row = new MarcadorCsvRow();
        row.setNumeroFila(numeroFila);
        row.setLineaOriginal(cruda);
        row.setDiaSemana(valor(headers, valores, "DÍA", "DIA"));
        row.setFecha(parseFecha(valor(headers, valores, "FECHA")));
        row.setDni(normalizarDni(valor(headers, valores, "DNI")));
        row.setNombre(valor(headers, valores, "NOMBRE"));
        row.setHoraEntradaEsperada(valor(headers, valores, "ENTRADA", "ENT.", "ENT"));
        row.setSalidaProgramada(valor(headers, valores, "SALIDA", "SAL."));
        row.setMarca1(valor(headers, valores, "MARCA1"));
        row.setMarca2(valor(headers, valores, "MARCA2"));
        row.setMarca3(valor(headers, valores, "MARCA3"));
        row.setMarca4(valor(headers, valores, "MARCA4"));
        row.setTardanza(valor(headers, valores, "TARD.", "TARD"));
        row.setEmpresa(valor(headers, valores, "EMPRESA"));
        row.setGrupo(valor(headers, valores, "GRUPO"));
        row.setRefrigerio(valor(headers, valores, "REFRIG.", "REFRIG"));
        row.setExcesoRefrigerio(valor(headers, valores, "E/REFRIG.", "E/REFRIG"));
        row.setTiempoRefrigerio(valor(headers, valores, "T/REFRIG", "T/REFRIG."));
        row.setTiempoAntesSalida(valor(headers, valores, "T/AS", "T/AS."));
        row.setHorasTrabajadas(valor(headers, valores, "H/TRAB.", "H/TRAB", "T/H.TRAB", "T/H.Trab"));
        row.setHorasExtra25(valor(headers, valores, "H25%"));
        row.setHorasExtra35(valor(headers, valores, "H35%"));
        row.setHorasExtra100(valor(headers, valores, "H100%"));
        row.setHorasExtraTotal(valor(headers, valores, "T/H.EXT", "T/H.Ext"));
        row.setObservacion(valor(headers, valores, "OBSERVACIONES", "OBSERVACIÓN", "OBSERVACION"));
        return row;
    }

    private String valor(String[] headers, String[] valores, String... candidatos) {
        for (String candidato : candidatos) {
            for (int i = 0; i < headers.length && i < valores.length; i++) {
                if (headers[i].trim().equalsIgnoreCase(candidato)) {
                    return valores[i].trim();
                }
            }
        }
        return "";
    }

    private LocalDate parseFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.trim();
        for (DateTimeFormatter formatter : FECHA_MARCADOR) {
            try {
                return LocalDate.parse(limpio, formatter);
            } catch (DateTimeParseException ignored) {
                // siguiente formato
            }
        }
        return null;
    }

    private String normalizarDni(String dni) {
        if (dni == null) {
            return "";
        }
        String limpio = dni.trim().replaceAll("\\D", "");
        if (limpio.isEmpty()) {
            return "";
        }
        try {
            return String.format("%08d", Long.parseLong(limpio));
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private int detectarCabecera(String[] lineas) {
        for (int i = 0; i < lineas.length; i++) {
            String upper = lineas[i].toUpperCase(Locale.ROOT);
            if (upper.contains("DNI") && upper.contains("FECHA") && upper.contains("MARCA1")) {
                return i;
            }
        }
        return -1;
    }

    private void validarSeparador(String texto) {
        for (String linea : texto.split("\\r?\\n")) {
            String trimmed = linea.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains(";")) {
                return;
            }
            if (trimmed.contains(",")) {
                throw new IllegalArgumentException(
                        "El archivo debe usar punto y coma (;) como separador del marcador.");
            }
        }
    }

    private boolean tieneDniValido(MarcadorCsvRow row) {
        return row.getDni() != null && row.getDni().length() == 8;
    }

    private boolean esCabeceraRepetida(String linea, String lineaCabecera) {
        return linea.equalsIgnoreCase(lineaCabecera);
    }

    private String[] splitSemicolon(String linea) {
        return linea.split(";", -1);
    }

    @lombok.Data
    public static class ParseResult {
        private String encoding;
        private List<MarcadorCsvRow> filas = new ArrayList<>();
    }
}
