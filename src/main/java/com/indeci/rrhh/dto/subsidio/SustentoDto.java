package com.indeci.rrhh.dto.subsidio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SustentoDto(
        @NotBlank(message = "El sustento es obligatorio") 
        @Size(min = 5, max = 255, message = "El sustento debe tener entre 5 y 255 caracteres") 
        String sustento
) {}
