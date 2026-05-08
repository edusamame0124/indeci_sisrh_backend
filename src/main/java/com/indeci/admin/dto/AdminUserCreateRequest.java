package com.indeci.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUserCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,64}$", message = "Usuario inválido (3-64 caracteres alfanuméricos, . _ -)")
    private String username;
}
