package com.indeci.rrhh.dto;

/**
 * Un cambio aplicado por {@code VacacionProvisionService.recalcularProvisionManual} —
 * "CREADO" (período ya cumplido que no tenía fila) o "CORREGIDO" (fila existente cuyo
 * Ganados no coincidía con el récord real recalculado).
 */
public record CorreccionSaldoDto(
        int anio,
        double ganadosAnterior,
        double ganadosNuevo,
        double gozados,
        String tipo) {
}
