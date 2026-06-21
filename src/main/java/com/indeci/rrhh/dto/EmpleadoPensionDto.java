package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmpleadoPensionDto {

    private Long empleadoId;

    private Long regimenPensionarioId;

    private String cuspp;

    private Double porcentajeAporte;

    private Double porcentajeComision;

    private Double porcentajeSeguro;

    private Long tipoComisionAfpId;

    private String tipoRegimen;

    private String condicionEspecialAfp;

    private LocalDate fechaCondicionAfp;

    private Long documentoSustentoId;

    private String observacionCondicionAfp;
}