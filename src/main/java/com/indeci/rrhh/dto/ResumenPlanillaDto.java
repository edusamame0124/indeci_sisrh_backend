package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class ResumenPlanillaDto {

    private Long empleadoId;

    private String periodo;

    private Double totalIngresos;

    private Double totalDescuentos;

    private Double netoPagar;

    // Spec 010 §5.4 / SERVIR-07 — validación neto 50%
    private Double neto50pctMinimo;

    /** 'BIEN' | 'NETO_NO_VA'. Alimenta el semáforo de PANTALLA-03 / §12.2. */
    private String estadoNeto;
}