package com.indeci.rrhh.dto;

import java.util.List;

/**
 * Elegibilidad CALCULADA del vínculo para planilla (F4a). No hay campo manual
 * "incluye en planilla": se deriva de los hechos del vínculo.
 *
 * @param elegiblePlanilla apto para planilla interna (vínculo vigente, régimen,
 *        remuneración, banco y régimen pensionario).
 * @param elegibleMcpp     además con AIRHSP válido (requerido para exportación MCPP).
 * @param cumple           checks satisfechos.
 * @param pendientes       checks faltantes (motivos de no elegibilidad).
 */
public record ElegibilidadVinculoDto(
        boolean elegiblePlanilla,
        boolean elegibleMcpp,
        List<String> cumple,
        List<String> pendientes) {
}
