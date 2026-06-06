package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class MovimientoPlanillaResponseDto {

    private Long id;

    private Long empleadoId;

    private String empleadoNombre;

    private String empleadoDni;

    private String regimenLaboralCodigo;

    private String regimenLaboralNombre;

    private String periodo;

    private Double totalIngresos;

    private Double totalDescuentos;

    private Double netoPagar;

    private String estado;

    private String observacion;

    private Integer activo;

    // Spec 010 §5.4 / SERVIR-07 — validación neto 50% (semáforo PANTALLA-01)
    private Double neto50pctMinimo;

    /** 'BIEN' | 'NETO_NO_VA'. */
    private String estadoNeto;
}
