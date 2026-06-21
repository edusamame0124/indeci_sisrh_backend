package com.indeci.rrhh.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmpleadoSaludEpsAnularInputDto {
    @NotBlank
    private String motivo;
}
