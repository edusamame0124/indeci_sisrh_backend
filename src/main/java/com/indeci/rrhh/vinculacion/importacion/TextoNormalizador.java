package com.indeci.rrhh.vinculacion.importacion;

import java.text.Normalizer;

/**
 * Saneo de texto del Excel de vinculación (Clase A del informe de verificación).
 *
 * <p>El Excel entregado por RR.HH. es un export con ruido de formato: espacios
 * duros (NBSP {@code  }), saltos de línea, apóstrofes/tildes sueltas, dobles
 * espacios y variantes de mayúsculas/acentos. Este componente centraliza ese saneo
 * para que reglas, resolver de catálogos y mapper no lo repitan (DRY).
 *
 * <p>Casos reales que motivan cada método (ver INFORME_VERIFICACION_EXCEL_OFICIAL.md):
 * <ul>
 *   <li>{@code '10705471393 '} (RUC con NBSP) → {@link #limpiar}</li>
 *   <li>{@code ' TÉCNICO'} vs {@code 'TECNICO '} → {@link #clave}</li>
 *   <li>{@code '´000104'} (AIRHSP), {@code '002-19410827680801198'} (CCI) → {@link #soloDigitos}</li>
 * </ul>
 */
public final class TextoNormalizador {

    private TextoNormalizador() {}

    /**
     * Saneo de presentación: quita NBSP y saltos, colapsa espacios y recorta.
     * Preserva mayúsculas y tildes (es el valor que se guarda).
     *
     * @return el texto saneado, o {@code null} si queda vacío (una celda con solo
     *         espacios equivale a celda vacía; caso real: NIVEL_POSGRADO {@code '         '}).
     */
    public static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        final String limpio = valor
                .replace(' ', ' ')   // NBSP → espacio normal
                .replace('\t', ' ')
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Clave de comparación para catálogos: {@link #limpiar} + MAYÚSCULAS + sin tildes.
     * Con esto {@code 'TÉCNICA COMPLETA'}, {@code 'tecnica completa '} y
     * {@code ' Tecnica Completa'} colapsan a la misma clave.
     *
     * @return la clave normalizada, o {@code null} si el texto queda vacío.
     */
    public static String clave(String valor) {
        final String limpio = limpiar(valor);
        if (limpio == null) {
            return null;
        }
        final String sinTildes = Normalizer.normalize(limpio, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toUpperCase();
    }

    /**
     * Conserva únicamente dígitos. Para identificadores numéricos que el Excel trae
     * con basura de formato: AIRHSP {@code '´000104'} → {@code '000104'};
     * CCI {@code '002-19410827680801198'} → {@code '00219410827680801198'}.
     *
     * <p><b>Importante:</b> devuelve String, nunca número — DNI, AIRHSP, meta, cuenta
     * y CCI tienen ceros a la izquierda que se perderían al convertir a numérico.
     *
     * @return solo los dígitos, o {@code null} si no queda ninguno.
     */
    public static String soloDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        final String digitos = valor.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /**
     * Rellena con ceros a la izquierda hasta {@code longitud}. Para códigos de ancho
     * fijo que el Excel pudo leer como número (AIRHSP de 6, DNI de 8).
     *
     * @return el valor con padding, o el original si ya es igual o más largo.
     */
    public static String padCeros(String valor, int longitud) {
        if (valor == null || valor.length() >= longitud) {
            return valor;
        }
        return "0".repeat(longitud - valor.length()) + valor;
    }
}
