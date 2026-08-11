package com.indeci.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body de POST /api/admin/users/{id}/clave — SUPER_ADMIN define una clave
 * temporal para un empleado que ya no recuerda la suya (soporte de mesa de
 * ayuda). A diferencia de /reset-password (solo marca NEW_CLAVE sin definir
 * valor), aquí el usuario podrá ingresar de inmediato con esta clave y el
 * sistema lo obligará a reemplazarla por una propia (mismo tratamiento que
 * la clave temporal de alta de usuario).
 */
@Data
public class AdminSetPasswordRequest {

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "La contraseña debe incluir mayúscula, minúscula, número y carácter especial")
    private String claveNueva;
}
