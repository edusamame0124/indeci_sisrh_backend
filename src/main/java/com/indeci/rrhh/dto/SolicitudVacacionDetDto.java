package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class SolicitudVacacionDetDto {

    private String tipo;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Double totalDias;
}