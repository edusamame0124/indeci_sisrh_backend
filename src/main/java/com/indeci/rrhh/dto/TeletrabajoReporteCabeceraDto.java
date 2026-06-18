package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class TeletrabajoReporteCabeceraDto {

    private Long empleadoId;

    private Integer mes;

    private Integer anio;

    private Long modalidadId;

    private LocalDate fechaReporte;
}