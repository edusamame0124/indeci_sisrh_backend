package com.indeci.rrhh.dto.subsidio;

import java.math.BigDecimal;

public record SubsidioLiquidacionExplicacionDto(
        Long liquidacionId,
        Integer versionLiq,
        String reglaVersion,
        String formulaAplicada,
        BigDecimal contraprestacionDiaria,
        BigDecimal contraprestacionEquivalente,
        BigDecimal subsidioDiarioEssalud,
        BigDecimal subsidioEstimado,
        BigDecimal diferencialIndeci,
        BigDecimal conciliacionTotal,
        Integer diasSubsidio,
        Integer diasLaborados,
        String tipoCaso,
        String snapshotJson) {}
