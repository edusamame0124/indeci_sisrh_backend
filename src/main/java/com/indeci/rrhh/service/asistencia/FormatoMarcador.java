package com.indeci.rrhh.service.asistencia;

/**
 * Formatos de reporte de marcador soportados por la carga de asistencia.
 *
 * <ul>
 *   <li>{@link #RELOJ1_DIARIO}: separador ';', una fila por dia, con DNI
 *       (marcador biometrico clasico INDECI).</li>
 *   <li>{@link #RELOJ2_COEN}: separador ',', log de eventos (una fila por
 *       marca), sin DNI, identidad por nombre (reporte COEN).</li>
 *   <li>{@link #DESCONOCIDO}: no coincide con ningun formato conocido.</li>
 * </ul>
 */
public enum FormatoMarcador {
    RELOJ1_DIARIO,
    RELOJ2_COEN,
    DESCONOCIDO
}
