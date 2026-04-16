package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPlanillaDto {

    private Long empleadoId;
    private Double sueldoBasico;
    private Double movilidad;
    private Double alimentacion;
    private Integer tieneAsignacionFamiliar;
    private Integer numHijos;
    private Double descuentoBanco;
    private Double descuentoInstitucion;
}