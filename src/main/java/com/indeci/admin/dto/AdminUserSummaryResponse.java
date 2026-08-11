package com.indeci.admin.dto;

import java.util.List;

public record AdminUserSummaryResponse(
        Long id,
        String username,
        String nombreCompleto,
        String status,
        List<AccesoSistemaDto> sistemas) {
}
