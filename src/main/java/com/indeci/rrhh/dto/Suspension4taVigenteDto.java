package com.indeci.rrhh.dto;

import java.time.LocalDate;

/**
 * FASE 1 — Resultado de la consulta de vigencia de suspensión de 4ta para el
 * motor de planilla.
 *
 * @param vigente       {@code true} solo si hay constancia ACTIVA cuya ventana
 *                      cubre la fecha de devengue.
 * @param existeVencida {@code true} si hay constancia ACTIVA pero su
 *                      FECHA_VIG_FIN ya pasó (para alertar en preflight).
 * @param nroConstancia N.° de operación/constancia SUNAT (si vigente).
 * @param fechaEmision  fecha de emisión de la constancia vigente.
 * @param fechaVigFin   fin de vigencia de la constancia vigente.
 */
public record Suspension4taVigenteDto(
        boolean vigente,
        boolean existeVencida,
        String nroConstancia,
        LocalDate fechaEmision,
        LocalDate fechaVigFin) {

    /** Empleado CAS sin ninguna constancia registrada → retiene (no suspende). */
    public static Suspension4taVigenteDto noRegistrada() {
        return new Suspension4taVigenteDto(false, false, null, null, null);
    }
}
