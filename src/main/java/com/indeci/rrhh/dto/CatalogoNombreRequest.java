package com.indeci.rrhh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Solicitud de alta/edición de catálogos nombre simple (bancos, tipos de cuenta). */
public record CatalogoNombreRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre no debe superar 200 caracteres")
        String name) {
}
