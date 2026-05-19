package com.indeci.rrhh.report.dto;

import lombok.Data;

@Data
public class PapeletaReportDto {

    private String institucion;

    private String nombres;

    private String area;

    private String cargo;

    private String tipo;

    private String dias;

    private String horas;

    private String desde;

    private String hasta;

    private String horaInicio;

    private String horaFin;

    private String motivo;

    private String fechaPapeleta;
}