package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class FormacionAcademicaDto {

    private Long empleadoId;

    private Long nivelInstruccionId;

    private Long gradoAcademicoId;

    private String institucion;

    private String carrera;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Integer egresado;

    private Integer bachiller;

    private Integer titulado;

    private String nroTitulo;

    private Long legajoDocumentoId;
}