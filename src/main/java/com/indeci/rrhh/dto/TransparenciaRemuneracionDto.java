package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 011 / B4 — M10 Transparencia (Ley 27806).
 * Fila pública de remuneración de un empleado en un período publicado.
 */
@Data
public class TransparenciaRemuneracionDto {

    private String empleado;

    private String regimen;

    /** Remuneración bruta del período (TOTAL_INGRESOS). */
    private Double remuneracionBruta;
}
