package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class TeletrabajoReporteDetResponseDto {

    private Long id;

    private Integer nroOrden;

    private String actividadProgramada;

    private String actividadEjecutada;

    private String medioVerificacion;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Long estadoCumplimientoId;

    private String estadoCumplimiento;

    private Double porcentajeAvance;

    private String incidenciaObservacion;

    private Long conformidadId;

    private String conformidad;
}