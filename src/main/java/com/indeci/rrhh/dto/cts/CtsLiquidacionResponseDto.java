package com.indeci.rrhh.dto.cts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Feature 016 — cabecera/estado de una liquidación de CTS trunca. */
public record CtsLiquidacionResponseDto(
        Long id,
        Long empleadoId,
        Long empleadoPlanillaId,
        String periodo,
        String regimenCodigo,
        String estrategia,
        LocalDate fechaIngreso,
        LocalDate fechaCese,
        int anios,
        int meses,
        int dias,
        BigDecimal baseComputable,
        BigDecimal montoAnios,
        BigDecimal montoFraccion,
        BigDecimal montoTotal,
        String estado) {
}
