package com.indeci.rrhh.service.asistencia;

/**
 * Conversión de tiempos del marcador (HH:mm) a minutos.
 */
public final class AsistenciaTiempoUtil {

    private AsistenciaTiempoUtil() {}

    public static int toMinutos(String valor) {
        if (valor == null || valor.isBlank()) {
            return 0;
        }
        String limpio = valor.trim();
        String[] partes = limpio.split(":");
        if (partes.length != 2) {
            return 0;
        }
        try {
            int horas = Integer.parseInt(partes[0].trim());
            int mins = Integer.parseInt(partes[1].trim());
            return Math.max(0, horas * 60 + mins);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static boolean tieneMarca(String valor) {
        return valor != null && !valor.isBlank();
    }
}
