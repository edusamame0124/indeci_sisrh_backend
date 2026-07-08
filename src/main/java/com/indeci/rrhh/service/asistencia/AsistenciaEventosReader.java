package com.indeci.rrhh.service.asistencia;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lector del reporte COEN ("Reloj 2"): CSV entrecomillado (coma) que es un
 * LOG DE EVENTOS (una fila por marca), sin DNI, con identidad por nombre.
 *
 * <p>Normaliza a la misma estructura diaria {@link MarcadorCsvRow} que el
 * Reloj 1 para reutilizar el pipeline (validacion, calculo, versionado). Por
 * cada trabajador+dia agrega sus marcas: entrada = primera marca, salida =
 * ultima. En dias de UNA sola marca (estado "Invalido" del reloj) la etiqueta
 * Ingreso/Salida NO es confiable, asi que se clasifica por la HORA y el dia se
 * marca como incompleto (lo tratara el validador segun SPEC D2).</p>
 *
 * <p>Sin estado: puede instanciarse con {@code new} (tests) o inyectarse.</p>
 */
@Component
public class AsistenciaEventosReader {

    /** Los ultimos 7 campos de cada linea son los datos reales de la marca. */
    private static final int CAMPOS_DATA = 7;

    /**
     * Corte para clasificar una marca huerfana (dia de una sola marca): antes =
     * probable ingreso; despues = probable salida. Heuristica del lector; el
     * calculo fino de tardanza usa la jornada del regimen (fase posterior).
     */
    private static final LocalTime CORTE_MEDIODIA = LocalTime.of(14, 0);

    private static final DateTimeFormatter[] FECHA = {
            fmt("dd/MM/uuuu"), fmt("d/M/uuuu"), fmt("d/MM/uuuu"),
    };

    private static final DateTimeFormatter[] HORA = {
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("H:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT),
    };

    private static DateTimeFormatter fmt(String p) {
        return DateTimeFormatter.ofPattern(p, Locale.ROOT).withResolverStyle(ResolverStyle.SMART);
    }

    public AsistenciaCsvParser.ParseResult parse(byte[] bytes) {
        String texto = MarcadorEncoding.decodificar(bytes);
        String[] lineas = texto.split("\\r?\\n");

        // 1. Extraer eventos (una marca por linea valida), agrupados por trabajador+dia.
        Map<String, DiaMarcas> porDia = new LinkedHashMap<>();
        for (int i = 0; i < lineas.length; i++) {
            String cruda = lineas[i];
            if (cruda == null || cruda.isBlank()) {
                continue;
            }
            MarcaEvento ev = parseEvento(cruda, i + 1);
            if (ev == null) {
                continue;
            }
            String clave = ev.nombreNorm + "|" + ev.fecha;
            porDia.computeIfAbsent(clave, k -> new DiaMarcas(ev.nombreOriginal, ev.fecha)).agregar(ev);
        }

        // 2. Consolidar cada dia en una fila diaria MarcadorCsvRow.
        List<MarcadorCsvRow> filas = new ArrayList<>();
        for (DiaMarcas dia : porDia.values()) {
            filas.add(consolidar(dia));
        }

        AsistenciaCsvParser.ParseResult result = new AsistenciaCsvParser.ParseResult();
        result.setEncoding(MarcadorEncoding.detectarEncoding(bytes));
        result.setFilas(filas);
        return result;
    }

    /** Interpreta una linea COEN tomando sus ultimos 7 campos (los datos reales). */
    private MarcaEvento parseEvento(String linea, int numeroLinea) {
        List<String> campos = parseCsv(linea);
        if (campos.size() < CAMPOS_DATA) {
            return null;
        }
        int base = campos.size() - CAMPOS_DATA;
        // [base+0]=#  [+1]=Trabajador  [+2]=Fecha  [+3]=Hora  [+4]=Estado  [+5]=Tipo  [+6]=Caracteristica
        String nombre = safe(campos.get(base + 1));
        LocalDate fecha = parseFecha(safe(campos.get(base + 2)));
        LocalTime hora = parseHora(safe(campos.get(base + 3)));
        if (nombre.isBlank() || fecha == null || hora == null) {
            return null; // linea de cabecera/metadatos sin marca real
        }
        MarcaEvento ev = new MarcaEvento();
        ev.nombreOriginal = nombre;
        ev.nombreNorm = normalizarNombre(nombre);
        ev.fecha = fecha;
        ev.hora = hora;
        ev.estado = safe(campos.get(base + 4));
        ev.caracteristica = safe(campos.get(base + 6));
        ev.numeroLinea = numeroLinea;
        return ev;
    }

    private MarcadorCsvRow consolidar(DiaMarcas dia) {
        dia.marcas.sort((a, b) -> a.hora.compareTo(b.hora));
        MarcaEvento primera = dia.marcas.get(0);
        MarcaEvento ultima = dia.marcas.get(dia.marcas.size() - 1);

        MarcadorCsvRow row = new MarcadorCsvRow();
        row.setNumeroFila(primera.numeroLinea);
        row.setLineaOriginal(dia.nombreOriginal + " " + dia.fecha);
        row.setDni("");                       // COEN no trae DNI -> se resuelve por alias
        row.setNombre(dia.nombreOriginal);
        row.setFecha(dia.fecha);

        boolean unaMarca = dia.marcas.size() == 1 || primera.hora.equals(ultima.hora);
        if (unaMarca) {
            // Dia incompleto ("Invalido") = FALTA en firme (SPEC D2). Se conserva la marca
            // real como referencia y se clasifica por HORA (no por la etiqueta del reloj,
            // que no es confiable). El prefijo "Falta" hace que el mapper lo tipifique FALTA;
            // si el trabajador presenta papeleta/teletrabajo, el día deja de descontar.
            if (primera.hora.isBefore(CORTE_MEDIODIA)) {
                row.setMarca1(hhmm(primera.hora));
                row.setObservacion("Falta: solo ingreso (sin salida).");
            } else {
                row.setMarca2(hhmm(primera.hora));
                row.setObservacion("Falta: solo salida (sin ingreso).");
            }
        } else {
            row.setMarca1(hhmm(primera.hora));   // entrada = primera marca
            row.setMarca2(hhmm(ultima.hora));    // salida  = ultima marca
        }
        return row;
    }

    // ── CSV quote-aware (RFC-4180 minimo: comillas, comas internas, "" escapada) ──
    private List<String> parseCsv(String linea) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean enComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (enComillas) {
                if (c == '"') {
                    if (i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        enComillas = false;
                    }
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                enComillas = true;
            } else if (c == ',') {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    private LocalDate parseFecha(String texto) {
        if (texto.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : FECHA) {
            try {
                return LocalDate.parse(texto, f);
            } catch (DateTimeParseException ignored) {
                // siguiente
            }
        }
        return null;
    }

    private LocalTime parseHora(String texto) {
        if (texto.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : HORA) {
            try {
                return LocalTime.parse(texto, f);
            } catch (DateTimeParseException ignored) {
                // siguiente
            }
        }
        return null;
    }

    /** Nombre para agrupar: mayusculas, espacios colapsados (tildes se conservan). */
    private String normalizarNombre(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String hhmm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private static final class MarcaEvento {
        private String nombreOriginal;
        private String nombreNorm;
        private LocalDate fecha;
        private LocalTime hora;
        private String estado;
        private String caracteristica;
        private int numeroLinea;
    }

    private static final class DiaMarcas {
        private final String nombreOriginal;
        private final LocalDate fecha;
        private final List<MarcaEvento> marcas = new ArrayList<>();

        private DiaMarcas(String nombreOriginal, LocalDate fecha) {
            this.nombreOriginal = nombreOriginal;
            this.fecha = fecha;
        }

        private void agregar(MarcaEvento ev) {
            marcas.add(ev);
        }
    }
}
