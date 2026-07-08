package com.indeci.rrhh.dto.cts;

import jakarta.validation.constraints.NotBlank;

public record CtsRegularRequestDto(
    @NotBlank(message = "El periodo es obligatorio")
    String periodo,
    Long regimenLaboralId
) {}
