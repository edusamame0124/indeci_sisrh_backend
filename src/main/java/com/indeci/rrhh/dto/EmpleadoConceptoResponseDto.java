package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmpleadoConceptoResponseDto {

    private Long id;

    private Long conceptoPlanillaId;

    private String concepto;

    private Double monto;

    private Double porcentaje;

    private String formula;

    /** Spec 013/C1 — vigencia de la asignación. */
    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Integer activo;
}