package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * F3.4 — Resultado de la ejecución del Asistente de Recálculo.
 *
 * <p>Por cada empleado del alcance se devuelve el resultado individual.
 * El service NO aborta cuando un empleado falla: captura el error y sigue
 * con el resto (mismo patrón que {@code generarTodoPeriodo}).</p>
 */
public record RecalculoResultadoDto(
        String periodo,
        int total,
        int exitosos,
        int fallidos,
        BigDecimal totalDelta,
        List<RecalculoResultadoItemDto> items) {

    public record RecalculoResultadoItemDto(
            Long empleadoId,
            String nombreCompleto,
            /** OK | ERROR */
            String status,
            BigDecimal netoAnterior,
            BigDecimal netoNuevo,
            BigDecimal delta,
            String razon) {}
}
