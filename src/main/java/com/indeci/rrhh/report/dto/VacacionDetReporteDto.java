package com.indeci.rrhh.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Fila del bloque "DETALLE DE LA SOLICITUD" de la papeleta de vacaciones. */
@Data
@AllArgsConstructor
public class VacacionDetReporteDto {

    private String fechaInicio;
    private String fechaFin;
    private String totalDias;
}
