package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/**
 * Resumen de planilla agrupado por meta presupuestal (SPEC §12.2 PANTALLA-05 —
 * hojas RES.COMPROMISO + RES.METAS).
 */
@Data
public class ResumenMetaDto {

    /** Meta presupuestal (INDECI_EMPLEADO_PLANILLA.META); "SIN META" si no tiene. */
    private String meta;

    /** Centro de costo asociado a la meta. */
    private String centroCosto;

    /** Población económicamente activa: número de empleados de la meta. */
    private Integer pea;

    private Double ingresos;

    private Double essalud;

    private Double aportes;

    /** Costo total para la entidad de la meta = ingresos + ESSALUD. */
    private Double total;

    /** Detalle expandible: empleados que componen la meta. */
    private List<ResumenMetaEmpleadoDto> empleados;
}
