package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class EmpleadoTurno24hResponseDto {

    private Long id;

    private Long empleadoId;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private String documentoAutorizacion;

    private String motivo;

    /** Vigencia respecto a hoy: VIGENTE / FUTURA / VENCIDA — la calcula el service, no el frontend. */
    private String estadoVigencia;
}
