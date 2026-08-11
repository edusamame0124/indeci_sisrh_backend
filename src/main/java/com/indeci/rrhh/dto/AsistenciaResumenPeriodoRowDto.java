package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Fila del reporte de asistencia consolidado por período (1 fila por empleado x
 * período), formato institucional {@code docs/reporte_asistencia.xlsx}. Se arma
 * a partir de {@code AsistenciaCabecera} (modelo de tardanza de dos niveles,
 * V010_95) + papeletas de permiso por asuntos personales aprobadas + datos de
 * persona/oficina vigentes en el período.
 */
@Data
public class AsistenciaResumenPeriodoRowDto {

    private String periodo;
    private String dni;
    private String apellidosNombres;
    private String regimen;
    private String direccionUnidad;

    private double remuneracionMensual;
    private double costoDia;
    private double costoHora;
    private double costoMinuto;

    // ---- TARDANZA (modelo de dos niveles, V010_95) ----
    private int minutosDescuento1;
    private double descuento1Diaria;
    private int minutosMenorAcumTotal;
    private int minutosExcedentesTope;
    private double descuento2Mensual;
    private int totalMinutosDescuento1y2;
    private double totalDescuento1y2;

    // ---- PERMISOS PARTICULARES (código "001" — Permiso por asuntos personales) ----
    private int minutosPermisosParticulares;
    private double descuentoPermisosParticulares;

    // ---- FALTAS ----
    private int totalFaltas;
    private double descuentoFaltas;

    // ---- SALIDA ANTICIPADA (modelo espejo de TARDANZA, V012_56) ----
    private int diasSalidaAnticipada;
    private int minutosSalidaAnticipada;
}
