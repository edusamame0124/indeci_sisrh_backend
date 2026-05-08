package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PeriodoPlanillaResponseDto {

    private Long id;

    private String periodo;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private String estado;

    private String observacion;

    private LocalDateTime fechaCierre;

    private Integer activo;
}