package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * F3.4 — Vista previa (dry-run) del Asistente de Recálculo.
 *
 * <p>Lista los empleados que recibirán el recálculo según el criterio
 * elegido. Solo lectura: no muta nada. Permite al usuario revisar el
 * alcance antes de confirmar.</p>
 */
public record RecalculoPreviewDto(
        String periodo,
        String criterioTipo,
        int total,
        List<RecalculoPreviewItemDto> items) {

    /** Item del preview. Incluye el neto actual del movimiento previo si existe. */
    public record RecalculoPreviewItemDto(
            Long empleadoId,
            String nombreCompleto,
            String regimenLaboralCodigo,
            BigDecimal netoActual,
            boolean tieneMovimientoPrevio) {}
}
