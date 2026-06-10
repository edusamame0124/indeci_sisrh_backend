package com.indeci.rrhh.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompensacionReporteDto {

    private String horas;
    private String fecha;
    private String horaInicio;
    private String horaFin;
}