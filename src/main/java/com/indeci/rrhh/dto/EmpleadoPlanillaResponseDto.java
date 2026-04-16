package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPlanillaResponseDto {

    private Long id;
    private Double sueldoBasico;
    private Double movilidad;
    private Double alimentacion;
    private Integer tieneAsignacionFamiliar;
    private Integer numHijos;
    private Integer activo;
}