package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PeriodoPlanillaDto {

    private String periodo;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private String observacion;
}