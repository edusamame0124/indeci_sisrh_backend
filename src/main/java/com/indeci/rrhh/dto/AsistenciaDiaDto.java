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

    /** LABORAL | FALTA | TARDANZA | LICENCIA | VACACIONES | DESCANSO | FERIADO | OBSERVADO. */
    private String tipoDia;

    private Integer minutosTardanza;

    private String observacion;

    private String diaSemana;
    private String marcaEntrada;
    private String marcaSalida;
    private String horaEntradaEsperada;
    private Integer minutosSalidaAnticipada;
    private Integer horasTrabajadasMin;
    private Integer horasExtra25Min;
    private Integer horasExtra35Min;
    private Integer horasExtra100Min;
    private Integer horasExtraTotalMin;
    private String origen;
}
