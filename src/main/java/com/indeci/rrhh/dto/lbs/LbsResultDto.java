package com.indeci.rrhh.dto.lbs;

import java.util.List;

public record LbsResultDto(
    int exitosos,
    int total,
    List<LbsErrorDto> fallidos
) {
    public record LbsErrorDto(
        Long empleadoId,
        String razon
    ) {}
}
