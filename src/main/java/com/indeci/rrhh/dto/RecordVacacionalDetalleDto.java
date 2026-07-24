package com.indeci.rrhh.dto;

import java.util.List;

/**
 * Detalle de récord vacacional (Opción A) — dos niveles para dar trazabilidad completa:
 *
 * <ul>
 *   <li><b>Nivel 1 — acumulado de la carrera</b> ({@link TiempoServicioDetalleDto}): antigüedad
 *       bruta, LSG/faltas/suspensiones TOTALES y tiempo efectivo total. Reconcilia con
 *       "Configuración Remunerativa / Vinculación" (mismo origen).</li>
 *   <li><b>Nivel 2 — desglose por período</b> ({@link PeriodoRecordDto}): cada año de servicio
 *       (aniversario a aniversario) con SUS incidencias y si cumplió récord (+30). Explica de
 *       dónde salen los "Días que corresponden".</li>
 * </ul>
 */
public record RecordVacacionalDetalleDto(
        boolean sinVinculo,
        TiempoServicioDetalleDto acumulado,
        List<PeriodoRecordDto> periodos) {
}
