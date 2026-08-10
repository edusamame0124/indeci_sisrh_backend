package com.indeci.rrhh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body de POST /api/rrhh/persona/me/clave — cambio de contraseña voluntario
 * del empleado autenticado (a diferencia de /api/auth/cambiar-clave, que es
 * exclusivo del flujo de clave temporal forzada del primer login).
 */
@Data
public class CambiarClavePropiaDto {

    @NotBlank(message = "Debe ingresar su contraseña actual")
    private String claveActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "La contraseña debe incluir mayúscula, minúscula, número y carácter especial")
    private String claveNueva;
}
