package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class SolicitudVacacionDetDto {

    private String tipo;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Double totalDias;

    /** Hub Vacacional — id del período origen elegido del dropdown (solo en detalles "_ACTUAL"). */
    private Long vacacionOrigenId;
}