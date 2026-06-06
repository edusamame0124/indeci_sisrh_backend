package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * FASE 2 — Línea del snapshot de trazabilidad para la pantalla de explicación.
 *
 * <p>Proyecta una fila de {@code INDECI_CALCULO_SNAPSHOT} a la UI read-only
 * "¿cómo se obtuvo S/ X?". El {@code parametrosJson} se envía tal cual (texto)
 * para que el front lo muestre como detalle expandible sin que el backend
 * imponga un esquema fijo por regla.</p>
 */
public record ExplicacionSnapshotDto(
        /** GENERAL | IR4TA_CAS | IR5TA | SUBSIDIO. */
        String regla,
        BigDecimal baseCalculo,
        BigDecimal resultado,
        /** Fórmula legible: "(1800.00) × 0.08 = 144.00". */
        String formula,
        /** Versión de parámetros usados (típicamente el año fiscal). */
        String versionParametros,
        /** Payload JSON con los parámetros específicos de la regla. */
        String parametrosJson) {
}
