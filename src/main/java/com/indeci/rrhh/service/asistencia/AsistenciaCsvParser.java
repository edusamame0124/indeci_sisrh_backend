package com.indeci.rrhh.service.asistencia;

import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AsistenciaCsvParser {

    private static final DateTimeFormatter FECHA_MARCADOR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ParseResult parse(byte[] bytes) {
        String texto = decodificar(bytes);
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
        result.setEncoding(detectarEncoding(bytes));
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
        row.setHoraEntradaEsperada(valor(headers, valores, "ENT.", "ENT"));
        row.setMarca1(valor(headers, valores, "MARCA1"));
        row.setMarca2(valor(headers, valores, "MARCA2"));
        row.setEmpresa(valor(headers, valores, "EMPRESA"));
        row.setGrupo(valor(headers, valores, "GRUPO"));
        row.setHorasTrabajadas(valor(headers, valores, "T/H.TRAB", "T/H.Trab"));
        row.setHorasExtra25(valor(headers, valores, "H25%"));
        row.setHorasExtra35(valor(headers, valores, "H35%"));
        row.setHorasExtra100(valor(headers, valores, "H100%"));
        row.setHorasExtraTotal(valor(headers, valores, "T/H.EXT", "T/H.Ext"));
        row.setTardanza(valor(headers, valores, "TARD.", "TARD"));
        row.setSalidaAnticipada(valor(headers, valores, "S/A.T", "S/A.t"));
        row.setObservacion(valor(headers, valores, "OBSERVACIÓN", "OBSERVACION"));
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
        try {
            return LocalDate.parse(texto.trim(), FECHA_MARCADOR);
        } catch (DateTimeParseException ex) {
            return null;
        }
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

    private String decodificar(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        String windows1252 = new String(bytes, Charset.forName("Windows-1252"));
        if (!windows1252.contains("\uFFFD")) {
            return windows1252;
        }
        return new String(bytes, Charset.forName("ISO-8859-1"));
    }

    private String detectarEncoding(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return "UTF-8";
        }
        String windows1252 = new String(bytes, Charset.forName("Windows-1252"));
        if (!windows1252.contains("\uFFFD")) {
            return "Windows-1252";
        }
        return "ISO-8859-1";
    }

    @lombok.Data
    public static class ParseResult {
        private String encoding;
        private List<MarcadorCsvRow> filas = new ArrayList<>();
    }
}
