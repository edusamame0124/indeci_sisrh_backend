package com.indeci.rrhh.dto.subsidio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubsidioLiquidacionResponseDto(
        Long id,
        Long tramoId,
        Integer versionLiq,
        String estado,
        BigDecimal contraprestacionDiaria,
        BigDecimal contraprestacionEquivalente,
        BigDecimal subsidioDiarioEssalud,
        BigDecimal subsidioEstimado,
        BigDecimal diferencialIndeci,
        BigDecimal conciliacionTotal,
        String formulaAplicada,
        LocalDateTime createdAt) {}
