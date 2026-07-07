package com.indeci.rrhh.dto.cts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Feature 016 — desglose de trazabilidad del cálculo (panel drawer).
 * Responde "¿de dónde salió este monto?" para auditoría (Contraloría).
 */
public record CtsDesgloseDto(
        Long id,
        String dni,
        String nombre,
        String regimenCodigo,
        String estrategia,
        LocalDate fechaIngreso,
        LocalDate fechaCese,
        int anios,
        int meses,
        int dias,
        long diasFraccion,
        BigDecimal baseComputable,
        BigDecimal factorAnual,
        int divisorDias,
        List<ConceptoExcluido> excluidos,
        BigDecimal montoAnios,
        BigDecimal montoFraccion,
        BigDecimal montoTotal,
        String formula,
        String marcoNormativo,
        String estado) {

    /** Concepto no computable mostrado en S/ 0.00 (transparencia de exclusión). */
    public record ConceptoExcluido(String concepto, BigDecimal monto) {}
}
