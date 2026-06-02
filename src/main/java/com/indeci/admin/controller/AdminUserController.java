package com.indeci.admin.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.admin.dto.AdminUserCreateRequest;
import com.indeci.admin.dto.AdminUserDetailResponse;
import com.indeci.admin.dto.AdminUserEmpleadoPutRequest;
import com.indeci.admin.dto.AdminUserPermisoDeniesPutRequest;
import com.indeci.admin.dto.AdminUserPermisoGrantsPutRequest;
import com.indeci.admin.dto.AdminUserRolesPutRequest;
import com.indeci.admin.dto.AdminUserStatusPatchRequest;
import com.indeci.admin.dto.AdminUserSummaryResponse;
import com.indeci.admin.dto.AccesoSistemaDto;
import com.indeci.admin.dto.AccesosPutRequest;
import com.indeci.admin.dto.PermisoDeniedResponse;
import com.indeci.admin.service.AdminUserService;
import com.indeci.common.dto.ApiResponse;
import com.indeci.security.auth.SisrhSecurityExpressions;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.ADM_USERS)
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<org.springframework.data.domain.Page<AdminUserSummaryResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sistema) {

        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(Math.max(page, 0), safeSize);
        var result = adminUserService.listUsers(q, status, sistema, pageable);
        return new ApiResponse<>("OK", "Usuarios", result);
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> detalle(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Detalle usuario", adminUserService.getUser(id));
    }

    @PostMapping
    public ApiResponse<AdminUserDetailResponse> crear(@Valid @RequestBody AdminUserCreateRequest body) {
        return new ApiResponse<>("OK", "Usuario creado", adminUserService.createUser(body));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> estado(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusPatchRequest body) {
        adminUserService.patchStatus(id, body);
        return new ApiResponse<>("OK", "Estado actualizado", null);
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<Void> roles(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserRolesPutRequest body) {
        adminUserService.putRoles(id, body);
        return new ApiResponse<>("OK", "Roles actualizados", null);
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        adminUserService.resetPassword(id);
        return new ApiResponse<>("OK", "Clave marcada para renovación", null);
    }

    /** Spec 011 / B2 — vincula (o desvincula) la cuenta con un empleado. */
    @PutMapping("/{id}/empleado")
    public ApiResponse<Void> empleado(
            @PathVariable Long id,
            @RequestBody AdminUserEmpleadoPutRequest body) {
        adminUserService.asignarEmpleado(id, body.getEmpleadoId());
        return new ApiResponse<>("OK", "Empleado vinculado", null);
    }

    @GetMapping("/{id}/permiso-denegados")
    public ApiResponse<List<PermisoDeniedResponse>> denegados(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Permisos denegados", adminUserService.listDenied(id));
    }

    @PutMapping("/{id}/permiso-denegados")
    public ApiResponse<Void> putDenegados(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserPermisoDeniesPutRequest body) {
        adminUserService.putDenied(id, body);
        return new ApiResponse<>("OK", "Denegaciones actualizadas", null);
    }

    @GetMapping("/{id}/permiso-otorgados")
    public ApiResponse<List<PermisoDeniedResponse>> otorgados(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Permisos otorgados", adminUserService.listGranted(id));
    }

    @PutMapping("/{id}/permiso-otorgados")
    public ApiResponse<Void> putOtorgados(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserPermisoGrantsPutRequest body) {
        adminUserService.putGranted(id, body);
        return new ApiResponse<>("OK", "Permisos otorgados actualizados", null);
    }

    @GetMapping("/{id}/accesos")
    @PreAuthorize(SisrhSecurityExpressions.SUPER_ADMIN)
    public ApiResponse<List<AccesoSistemaDto>> accesos(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Accesos por sistema", adminUserService.getAccesos(id));
    }

    @PutMapping("/{id}/accesos")
    @PreAuthorize(SisrhSecurityExpressions.SUPER_ADMIN)
    public ApiResponse<Void> putAccesos(
            @PathVariable Long id,
            @Valid @RequestBody AccesosPutRequest body) {
        adminUserService.putAccesos(id, body);
        return new ApiResponse<>("OK", "Accesos actualizados", null);
    }
}
