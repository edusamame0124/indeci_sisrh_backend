package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * B3 / M09 — Request para alta/edición de una suspensión/licencia (fuente del .snl).
 */
@Data
public class SuspensionDto {

    private Long empleadoId;
    private String codSuspension;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer diasAfectos;
    private String nroCmp;
    private String nroResolucion;
    private String observacion;
}
