package com.indeci.rrhh.dto;

import java.time.LocalDateTime;

/**
 * Trazabilidad Visual (Padrón Vacacional) — una fila del ciclo de vida completo del saldo de
 * un año, incluyendo las anuladas por "Provisionar Auto" (activo=0). A diferencia de
 * {@link VacacionSaldoResponseDto} (solo-lectura del Portal del Empleado, solo filas activas),
 * este DTO expone {@code origen} y {@code activo} para el modal "Historial de Recálculos".
 */
public record HistorialSaldoDto(
        Long id,
        Integer anio,
        Double diasGanados,
        Double diasGozados,
        Double diasSaldo,
        String origen,
        Integer activo,
        String observacion,
        LocalDateTime createdAt) {
}
