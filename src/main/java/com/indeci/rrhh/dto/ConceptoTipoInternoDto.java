package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * SPEC_CONCEPTOS_PLANILLA §13 — fila del catálogo "Tipo de Concepto" (SISPER)
 * expuesta a la UI.
 *
 * <p>{@code clasificacionMotor} permite que el wizard muestre, bajo el select,
 * la clasificación del motor derivada ("Clasificación del motor: …") sin queries
 * adicionales.</p>
 */
@Data
public class ConceptoTipoInternoDto {

    private String codigo;
    private String nombre;
    private String clasificacionMotor;
    private Integer orden;
}
