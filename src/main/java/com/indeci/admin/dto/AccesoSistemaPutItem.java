package com.indeci.admin.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccesoSistemaPutItem(
        @NotBlank
        @Size(max = 30)
        String codigo,

        @NotNull
        Boolean activo,

        @NotNull
        @Size(max = 20)
        List<@NotBlank @Size(max = 40) String> roles,

        @Size(max = 40)
        String area) {
}
