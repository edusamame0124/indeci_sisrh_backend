package com.indeci.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,64}$", message = "Usuario inválido (3-64 caracteres alfanuméricos, . _ -)")
    private String username;

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "^\\d{8}$", message = "El DNI debe tener 8 dígitos")
    private String dni;

    /** Clave temporal definida por TI; el usuario deberá cambiarla en el primer ingreso (NEW_CLAVE). */
    @NotBlank(message = "La contraseña temporal es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "La contraseña debe incluir mayúscula, minúscula, número y carácter especial")
    private String password;
}
