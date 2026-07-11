package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Actividad del día de una papeleta de Teletrabajo (Ley N° 31572 / SERVIR).
 * Patrón: {@link SolicitudVacacionDetDto}.
 */
@Data
public class SolicitudTeletrabajoDetDto {

    private Integer nroOrden;

    private String actividad;

    private String medioVerificacion;

    private String evidenciaArchivo;
}
