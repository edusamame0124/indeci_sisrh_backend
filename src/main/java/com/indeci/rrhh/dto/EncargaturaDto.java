package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * F5.2 — Request body para crear / actualizar una encargatura.
 *
 * <p>Coincide con {@link com.indeci.rrhh.entity.EmpleadoEncargatura}. El
 * service valida que titular ≠ encargado, que la fecha fin sea posterior o
 * igual a la fecha inicio, y que no haya solape activo para el reemplazante.</p>
 */
@Data
public class EncargaturaDto {

    private Long empleadoTitularId;
    private Long empleadoEncargId;
    private LocalDate fechaInicio;
    /** Null en encargaturas indefinidas (cierre manual). */
    private LocalDate fechaFin;
    private String resolucion;
}
