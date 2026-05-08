package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPensionResponseDto {

    private Long id;

    private Long regimenPensionarioId;

    private String cuspp;

    private Double porcentajeAporte;

    private Double porcentajeComision;

    private Double porcentajeSeguro;

    private Long tipoComisionAfpId;

    private String tipoRegimen;

    private Integer activo;
    
    private String regimenPensionario;

    private String tipoComisionAfp;
}