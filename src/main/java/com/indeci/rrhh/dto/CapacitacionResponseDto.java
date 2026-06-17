package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class CapacitacionResponseDto {

    private Long id;

    private Long empleadoId;

    private String nombreCurso;

    private String institucion;

    private Integer horas;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Integer certificado;

    private Long legajoDocumentoId;
}