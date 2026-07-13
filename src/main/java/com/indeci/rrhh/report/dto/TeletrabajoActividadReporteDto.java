package com.indeci.rrhh.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Fila de "ACTIVIDADES DEL DÍA" de la papeleta de teletrabajo (número + descripción). */
@Data
@AllArgsConstructor
public class TeletrabajoActividadReporteDto {

    private String numero;
    private String actividad;
}
