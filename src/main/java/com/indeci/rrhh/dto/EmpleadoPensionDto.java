package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPensionDto {

    private Long empleadoId;
    private Long afpId;
    private String tipo;
    private String cuspp;
    private Double porcentajeAporte;
    private Double porcentajeComision;
    private Double porcentajeSeguro;
}