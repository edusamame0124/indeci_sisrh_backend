package com.indeci.rrhh.service.asistencia;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Detecta el formato de un reporte de marcador (Reloj 1 diario vs Reloj 2 / COEN)
 * a partir del contenido. Permite que la carga de asistencia acepte ambos
 * archivos en el mismo flujo, eligiendo el lector adecuado (SPEC D8).
 *
 * <p>Sin estado: puede instanciarse con {@code new} (tests) o inyectarse.</p>
 */
@Component
public class FormatoDetector {

    /** Titulo distintivo del reporte de eventos COEN. */
    private static final String TITULO_COEN = "REPORTE DE MARCAS DEL PERSONAL";

    public FormatoMarcador detectar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return FormatoMarcador.DESCONOCIDO;
        }
        return detectar(MarcadorEncoding.decodificar(bytes));
    }

    public FormatoMarcador detectar(String texto) {
        if (texto == null || texto.isBlank()) {
            return FormatoMarcador.DESCONOCIDO;
        }
        String upper = texto.toUpperCase(Locale.ROOT);

        // COEN (Reloj 2): titulo del reporte de eventos, o cabecera de eventos
        // entrecomillada (coma). El titulo es lo mas distintivo y suficiente.
        if (upper.contains(TITULO_COEN)
                || (upper.contains("\"TRABAJADOR\"") && upper.contains("\"CARACTER"))) {
            return FormatoMarcador.RELOJ2_COEN;
        }

        // Reloj 1 (diario): separador ';' y una cabecera con DNI + FECHA + MARCA1.
        if (upper.indexOf(';') >= 0 && tieneCabeceraDiaria(upper)) {
            return FormatoMarcador.RELOJ1_DIARIO;
        }

        return FormatoMarcador.DESCONOCIDO;
    }

    private boolean tieneCabeceraDiaria(String upper) {
        for (String linea : upper.split("\\r?\\n")) {
            if (linea.contains("DNI") && linea.contains("FECHA") && linea.contains("MARCA1")) {
                return true;
            }
        }
        return false;
    }
}
