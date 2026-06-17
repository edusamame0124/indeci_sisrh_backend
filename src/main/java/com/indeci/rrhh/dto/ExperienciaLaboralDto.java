package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class ExperienciaLaboralDto {

    private Long empleadoId;

    private String empresa;

    private String cargo;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private String funciones;

    private Long legajoDocumentoId;
}