package com.indeci.rrhh.vinculacion.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una fila cruda de la hoja {@code VINCULACION}, ya leída del Excel pero <b>sin
 * validar ni resolver catálogos</b>.
 *
 * <p>Guarda el valor tal como lo entregó POI ({@code String}, {@code Double},
 * {@code LocalDateTime}...) y expone accesores que absorben la suciedad real del
 * archivo de RR.HH.:
 * <ul>
 *   <li>{@link #numero} entiende {@code "S/. 18,707.14"} (4 casos reales).</li>
 *   <li>{@link #fecha} tolera fecha real, texto {@code dd/MM/yyyy} y basura → {@code null}.</li>
 *   <li>{@link #digitos} rescata {@code '´000104'} y {@code '002-1941...'}.</li>
 * </ul>
 *
 * <p>Inmutable desde fuera: se construye con {@link #put} solo desde el lector.
 */
public class VinculacionRowRaw {

    private static final DateTimeFormatter[] FORMATOS_FECHA = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    };

    /** Primer número del texto, con miles opcionales: extrae '18,707.14' de 'S/. 18,707.14'. */
    private static final Pattern NUMERO = Pattern.compile("-?\\d[\\d,]*(?:\\.\\d+)?");

    /** Número de fila tal como se ve en Excel (1-based) — para mensajes al usuario. */
    private final int numeroFila;
    private final Map<VinculacionColumna, Object> valores = new EnumMap<>(VinculacionColumna.class);

    public VinculacionRowRaw(int numeroFila) {
        this.numeroFila = numeroFila;
    }

    void put(VinculacionColumna columna, Object valor) {
        if (valor != null) {
            valores.put(columna, valor);
        }
    }

    public int getNumeroFila() {
        return numeroFila;
    }

    /** {@code true} si la fila no tiene ningún dato (relleno al final de la hoja). */
    public boolean estaVacia() {
        return valores.isEmpty();
    }

    /** Valor crudo, útil para distinguir tipo en las reglas. */
    public Object crudo(VinculacionColumna columna) {
        return valores.get(columna);
    }

    /**
     * Texto saneado (NBSP, saltos, dobles espacios). Los números enteros se devuelven
     * sin el {@code .0} que agrega POI (p. ej. meta {@code 44}, no {@code 44.0}).
     *
     * @return texto limpio o {@code null} si la celda está vacía.
     */
    public String texto(VinculacionColumna columna) {
        final Object v = valores.get(columna);
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return TextoNormalizador.limpiar(s);
        }
        if (v instanceof LocalDateTime dt) {
            return dt.toLocalDate().toString();
        }
        if (v instanceof Double d) {
            return d == Math.floor(d) && !d.isInfinite()
                    ? String.valueOf(d.longValue())
                    : BigDecimal.valueOf(d).toPlainString();
        }
        return TextoNormalizador.limpiar(String.valueOf(v));
    }

    /** Clave normalizada (MAYÚSCULAS, sin tildes) para comparar contra catálogos. */
    public String clave(VinculacionColumna columna) {
        return TextoNormalizador.clave(texto(columna));
    }

    /** Solo dígitos — para DNI, RUC, AIRHSP, CCI, cuenta. Preserva ceros a la izquierda. */
    public String digitos(VinculacionColumna columna) {
        return TextoNormalizador.soloDigitos(texto(columna));
    }

    /**
     * Fecha de la celda. Acepta fecha real de Excel o texto con formato conocido.
     * Cualquier otra cosa ({@code "Indeterminado"}, {@code "-"}, {@code "CONFIANZA"})
     * devuelve {@code null}: la regla correspondiente decide si eso es error.
     */
    public LocalDate fecha(VinculacionColumna columna) {
        final Object v = valores.get(columna);
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime dt) {
            return dt.toLocalDate();
        }
        if (v instanceof java.util.Date d) {
            return new java.sql.Timestamp(d.getTime()).toLocalDateTime().toLocalDate();
        }
        final String s = texto(columna);
        if (s == null) {
            return null;
        }
        for (DateTimeFormatter f : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(s, f);
            } catch (Exception ignorado) {
                // se intenta el siguiente formato
            }
        }
        return null;
    }

    /**
     * Número de la celda. Tolera moneda con símbolo y separador de miles
     * ({@code "S/. 18,707.14"} → {@code 18707.14}).
     *
     * @return el valor, o {@code null} si la celda no contiene un número reconocible.
     */
    public BigDecimal numero(VinculacionColumna columna) {
        final Object v = valores.get(columna);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        final String s = texto(columna);
        if (s == null) {
            return null;
        }
        // Se extrae el número en vez de "limpiar" caracteres: en 'S/. 18,707.14' el punto
        // del símbolo de moneda quedaría pegado al número y rompería el parseo.
        final Matcher m = NUMERO.matcher(s);
        if (!m.find()) {
            return null;
        }
        try {
            return new BigDecimal(m.group().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Booleano tolerante: {@code S/SI/SÍ/1/X} → true; {@code N/NO/0} → false.
     * Cualquier otro valor (p. ej. {@code "CONFIANZA"}) se resuelve en su regla.
     *
     * @return {@code null} si la celda está vacía o el valor no es reconocible.
     */
    public Boolean logico(VinculacionColumna columna) {
        final String k = clave(columna);
        if (k == null) {
            return null;
        }
        return switch (k) {
            case "S", "SI", "1", "X", "TRUE" -> Boolean.TRUE;
            case "N", "NO", "0", "FALSE" -> Boolean.FALSE;
            default -> null;
        };
    }
}
