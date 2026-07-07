package com.indeci.rrhh.dto.cts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Feature 016 — solicitud de cálculo de una liquidación de CTS trunca. */
public record CtsCalcularRequest(
        @NotNull Long empleadoId,
        @NotNull Long empleadoPlanillaId,
        @NotBlank String periodo) {
}
