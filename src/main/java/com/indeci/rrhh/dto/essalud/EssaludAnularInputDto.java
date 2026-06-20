package com.indeci.rrhh.dto.essalud;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EssaludAnularInputDto {
    @NotBlank(message = "El motivo de anulación es obligatorio.")
    private String motivo;
}
