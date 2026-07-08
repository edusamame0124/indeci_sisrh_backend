package com.indeci.rrhh.dto.cts;

import java.util.List;

public record CtsRegularResultDto(
    int exitosos,
    int total,
    List<CtsErrorDto> fallidos
) {
    public record CtsErrorDto(
        Long empleadoId,
        String razon
    ) {}
}
