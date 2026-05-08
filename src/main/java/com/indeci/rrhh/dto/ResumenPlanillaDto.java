package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class ResumenPlanillaDto {

    private Long empleadoId;

    private String periodo;

    private Double totalIngresos;

    private Double totalDescuentos;

    private Double netoPagar;
}