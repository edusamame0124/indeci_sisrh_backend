package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class MovimientoPlanillaResponseDto {

    private Long id;

    private Long empleadoId;

    private String periodo;

    private Double totalIngresos;

    private Double totalDescuentos;

    private Double netoPagar;

    private String estado;

    private String observacion;

    private Integer activo;
}