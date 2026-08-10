package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class EmpleadoJornadaExcepcionDto {

    private Long empleadoId;

    private String horaIngreso;

    private String horaSalida;

    private String refrigerioInicio;

    private String refrigerioFin;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private String documentoAutorizacion;

    private String motivo;
}
