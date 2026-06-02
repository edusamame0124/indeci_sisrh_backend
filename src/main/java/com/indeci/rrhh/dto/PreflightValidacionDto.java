package com.indeci.rrhh.dto;

import java.util.List;

/**
 * F3.3 — Respuesta del Centro de Validaciones.
 *
 * <p>Resultado del preflight del período: cuenta hallazgos por severidad y
 * devuelve la lista completa. Sin paginación — usualmente el universo es
 * &lt; 200 hallazgos.</p>
 *
 * <p>Si {@code totalBloqueos == 0} la UI muestra el CTA "Generar planilla".</p>
 */
public record PreflightValidacionDto(
        String periodo,
        int totalBloqueos,
        int totalAlertas,
        int totalInfo,
        List<ValidacionHallazgoDto> hallazgos) {

    public static PreflightValidacionDto desdeLista(
            String periodo, List<ValidacionHallazgoDto> hallazgos) {
        int b = 0, a = 0, i = 0;
        for (ValidacionHallazgoDto h : hallazgos) {
            switch (h.severidad()) {
                case "BLOQUEO" -> b++;
                case "ALERTA"  -> a++;
                case "INFO"    -> i++;
                default        -> { /* ignorar */ }
            }
        }
        return new PreflightValidacionDto(periodo, b, a, i, hallazgos);
    }
}
