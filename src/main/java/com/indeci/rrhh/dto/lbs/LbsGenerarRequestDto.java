package com.indeci.rrhh.dto.lbs;

import jakarta.validation.constraints.NotBlank;

public record LbsGenerarRequestDto(
    @NotBlank(message = "El periodo es obligatorio")
    String periodo,
    Long regimenLaboralId
) {}
