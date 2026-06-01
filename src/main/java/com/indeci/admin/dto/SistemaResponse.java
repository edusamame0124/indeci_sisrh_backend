package com.indeci.admin.dto;

public record SistemaResponse(
        String codigo,
        String nombre,
        String descripcion,
        String icono,
        Integer orden,
        Integer activo) {
}
