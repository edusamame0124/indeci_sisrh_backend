package com.indeci.admin.dto;

import java.util.List;

public record AdminUserDetailResponse(
        Long id,
        String username,
        String dni,
        String status,
        List<Long> assignedRoleIds,
        List<Long> deniedPermissionIds,
        List<AccesoSistemaDto> sistemas) {
}
