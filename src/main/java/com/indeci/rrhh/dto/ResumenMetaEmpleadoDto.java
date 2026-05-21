package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Línea de empleado dentro del resumen por meta (SPEC §12.2 PANTALLA-05).
 * Es el detalle expandible de cada meta presupuestal.
 */
@Data
public class ResumenMetaEmpleadoDto {

    private Long empleadoId;

    /** Remuneración bruta del empleado en el período (TOTAL_INGRESOS). */
    private Double ingresos;

    /** ESSALUD empleador (MEF 06001 / 06002). */
    private Double essalud;

    /** Aportes pensionarios del trabajador (MEF 05001-05004). */
    private Double aportes;

    /** Costo para la entidad = ingresos + ESSALUD. */
    private Double total;
}
