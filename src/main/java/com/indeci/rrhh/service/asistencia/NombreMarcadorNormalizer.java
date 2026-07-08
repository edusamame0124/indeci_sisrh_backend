package com.indeci.rrhh.service.asistencia;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza el nombre del marcador para emparejarlo con el alias del empleado
 * (SPEC D1). Reglas: sin tildes/diacríticos, mayúsculas, espacios colapsados.
 * Debe usarse igual al leer (agrupar) y al guardar/buscar el alias, para que
 * "José" ≡ "JOSE" y "ACUÑA" ≡ "ACUNA".
 */
public final class NombreMarcadorNormalizer {

    private NombreMarcadorNormalizer() {}

    public static String normalizar(String nombre) {
        if (nombre == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
