package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class EmpleadoTurno24hDto {

    private Long empleadoId;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private String documentoAutorizacion;

    private String motivo;
}
