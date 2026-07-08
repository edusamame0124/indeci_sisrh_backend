package com.indeci.rrhh.service.asistencia;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Decodificacion de archivos de marcador (Reloj 1 y Reloj 2 / COEN).
 *
 * <p>Los relojes exportan en codificaciones distintas y a veces con BOM: se
 * intenta UTF-8 y, si aparece el caracter de reemplazo (U+FFFD), se prueba
 * Windows-1252 y luego ISO-8859-1. Fuente unica para no duplicar la logica
 * entre el parser diario y el lector de eventos.</p>
 */
public final class MarcadorEncoding {

    /** Caracter de reemplazo U+FFFD (aparece cuando el charset elegido no calza). */
    private static final String REEMPLAZO = Character.toString(0xFFFD);
    /** BOM UTF-8 (U+FEFF) al inicio del texto. */
    private static final char BOM = (char) 0xFEFF;

    private MarcadorEncoding() {}

    /** Texto decodificado con el mejor charset detectado y sin BOM. */
    public static String decodificar(byte[] bytes) {
        byte[] sinBom = quitarBomBytes(bytes);
        String utf8 = stripBom(new String(sinBom, StandardCharsets.UTF_8));
        if (!utf8.contains(REEMPLAZO)) {
            return utf8;
        }
        String windows1252 = stripBom(new String(sinBom, Charset.forName("Windows-1252")));
        if (!windows1252.contains(REEMPLAZO)) {
            return windows1252;
        }
        return stripBom(new String(sinBom, Charset.forName("ISO-8859-1")));
    }

    /** Nombre del charset detectado (para trazabilidad en la importacion). */
    public static String detectarEncoding(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains(REEMPLAZO)) {
            return "UTF-8";
        }
        String windows1252 = new String(bytes, Charset.forName("Windows-1252"));
        if (!windows1252.contains(REEMPLAZO)) {
            return "Windows-1252";
        }
        return "ISO-8859-1";
    }

    private static byte[] quitarBomBytes(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            byte[] trimmed = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static String stripBom(String texto) {
        if (texto != null && !texto.isEmpty() && texto.charAt(0) == BOM) {
            return texto.substring(1);
        }
        return texto;
    }
}
