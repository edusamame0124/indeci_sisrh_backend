package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmpleadoConceptoDto {

    private Long empleadoId;

    private Long conceptoPlanillaId;

    private Double monto;

    private Double porcentaje;

    private String formula;

    /** Spec 013/C1 — vigencia: mes/año de inicio (obligatorio en el modal). */
    private LocalDate fechaInicio;

    /** Spec 013/C1 — vigencia: mes/año de fin. NULL = indefinido. */
    private LocalDate fechaFin;
}