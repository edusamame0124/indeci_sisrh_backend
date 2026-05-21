package com.indeci.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Spec 013 / C1 — Resultado de la estimación de neto del modal "Asignar
 * Descuento / Ajuste Manual" (preview en tiempo real, sin grabar en BD).
 *
 * <p>Nombre con sufijo {@code Dto} por convención del proyecto
 * (cf. {@code EmpleadoConceptoDto}, {@code ConceptoPlanillaResponseDto}).
 */
@Data
public class EstimacionNetoDto {

    /** Neto del empleado con sus conceptos activos actuales (sin el descuento nuevo). */
    private BigDecimal netoActual;

    /** Neto proyectado si se agregara el descuento propuesto. */
    private BigDecimal netoEstimado;

    /** {@code netoEstimado − netoActual} (normalmente negativo: es un descuento). */
    private BigDecimal diferencia;

    /** {@code true} si el neto estimado respeta la REGLA SERVIR-07 (50%). */
    private boolean cumpleRegla50;

    /** Mensaje de alerta cuando {@code cumpleRegla50 == false}; {@code null} si cumple. */
    private String mensajeAlerta;
}
