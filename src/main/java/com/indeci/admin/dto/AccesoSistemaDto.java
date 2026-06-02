package com.indeci.admin.dto;

import java.util.List;

public record AccesoSistemaDto(
        String codigo,
        String nombre,
        Boolean activo,
        List<String> roles,
        String area) {
}
