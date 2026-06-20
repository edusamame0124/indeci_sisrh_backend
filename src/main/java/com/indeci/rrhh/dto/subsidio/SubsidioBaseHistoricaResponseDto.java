package com.indeci.rrhh.dto.subsidio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SubsidioBaseHistoricaResponseDto(
        Long id,
        Long casoId,
        Integer mesesEvaluados,
        Integer divisorPromedio,
        BigDecimal topeMensual,
        BigDecimal baseReconocida,
        String fuente,
        Integer versionBase,
        LocalDateTime createdAt,
        List<SubsidioBaseDetalleDto> detalle) {}
