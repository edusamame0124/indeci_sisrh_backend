package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Fila del reporte de marcaciones diarias (1 fila por día x empleado), formato
 * institucional {@code docs/EJEMPLO REPORTE EXCEL SISTEMA.xlsx}. Se arma sobre
 * {@link AsistenciaDiariaRowDto} (misma proyección que "Consulta diaria de
 * asistencia") agregando régimen, unidad orgánica vigente ese día, día de la
 * semana y horario de salida programado.
 */
@Data
public class AsistenciaMarcacionRowDto {

    private String dni;
    private String nombreCompleto;
    private String regimen;
    private String unidadOrganica;
    private String diaSemana;
    private LocalDate fecha;
    private String horarioEntrada;
    private String horarioSalida;
    private String marcaEntrada;
    private String marcaSalida;
    /** Diferencia simple horario vs. marcación (llegó antes + salió después), en minutos. */
    private Integer horasExtraDiaMin;
    private String condicion;
    private String permisoPapeletaTeletrabajo;

    /**
     * Minutos que la marcación de salida ocurrió antes del horario de salida
     * programado; {@code null} si no hubo salida anticipada o no se pudo
     * determinar (marca incompleta / sin horario). Alimenta las columnas
     * "Salida anticipada" (Sí/No) y "Horas/Minutos dejados de trabajar".
     */
    private Integer minutosSalidaAnticipada;
}
