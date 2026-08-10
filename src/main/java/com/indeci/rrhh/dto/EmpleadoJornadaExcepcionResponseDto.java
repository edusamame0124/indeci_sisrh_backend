package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class EmpleadoJornadaExcepcionResponseDto {

    private Long id;

    private Long empleadoId;

    private String horaIngreso;

    private String horaSalida;

    private String refrigerioInicio;

    private String refrigerioFin;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private String documentoAutorizacion;

    private String motivo;

    /** Vigencia respecto a hoy: VIGENTE / FUTURA / VENCIDA — la calcula el service, no el frontend. */
    private String estadoVigencia;
}
