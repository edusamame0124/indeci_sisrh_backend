package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPensionResponseDto {

    private Long id;
    private Long afpId;
    private String tipo;
    private String cuspp;
    private Double porcentajeAporte;
    private Integer activo;
}