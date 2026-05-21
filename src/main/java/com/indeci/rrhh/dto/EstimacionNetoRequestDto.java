package com.indeci.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Spec 013 / C1 — Cuerpo del POST {@code /api/rrhh/empleados/{id}/estimar-neto}.
 * El descuento propuesto que el modal quiere previsualizar (sin grabar).
 */
@Data
public class EstimacionNetoRequestDto {

    /** Concepto de planilla del descuento propuesto. */
    private Long conceptoId;

    /** Monto mensual del descuento propuesto. */
    private BigDecimal monto;
}
