package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class SolicitudCompensacionDetDto {

    private LocalDate fechaCompensacion;

    private String horaInicio;

    private String horaFin;

    private Double cantidadHoras;
}