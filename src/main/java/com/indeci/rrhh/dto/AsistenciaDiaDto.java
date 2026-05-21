package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Día calendario de la asistencia (M04 / SPEC §12.2 PANTALLA-02).
 * Sirve tanto de entrada (carga) como de salida (consulta).
 */
@Data
public class AsistenciaDiaDto {

    private LocalDate dia;

    /** LABORAL | FALTA | TARDANZA | LICENCIA | VACACIONES | DESCANSO. */
    private String tipoDia;

    private Integer minutosTardanza;

    private String observacion;
}
