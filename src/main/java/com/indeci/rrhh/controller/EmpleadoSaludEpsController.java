package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.*;
import com.indeci.rrhh.service.EmpleadoSaludEpsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Módulo Salud / EPS del empleado.
 * Gestiona la cobertura de salud (Solo EsSalud o EsSalud+EPS) por empleado.
 * El motor de planilla usa esta configuración para distribuir el aporte del empleador.
 * Normativa: D.S. 009-97-SA — aporte empleador EsSalud 9%.
 */
@RestController
@RequestMapping("/api/rrhh/empleados/{empleadoId}/salud-eps")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('PLA_WRITE')")
public class EmpleadoSaludEpsController {

    private final EmpleadoSaludEpsService service;

    @GetMapping("/eps")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('PLA_WRITE') or hasAuthority('PLA_READ')")
    public ApiResponse<List<EpsDto>> listarEps() {
        return new ApiResponse<>("OK", "Catálogo EPS", service.listarEps());
    }

    @GetMapping("/actual")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('PLA_WRITE') or hasAuthority('PLA_READ')")
    public ApiResponse<EmpleadoSaludEpsDto> actual(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Cobertura actual", service.actual(empleadoId));
    }

    @GetMapping("/historial")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('PLA_WRITE') or hasAuthority('PLA_READ')")
    public ApiResponse<List<EmpleadoSaludEpsDto>> historial(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Historial Salud/EPS", service.historial(empleadoId));
    }

    @PostMapping
    public ApiResponse<EmpleadoSaludEpsDto> crear(
            @PathVariable Long empleadoId,
            @Valid @RequestBody EmpleadoSaludEpsInputDto input) {
        return new ApiResponse<>("OK", "Cobertura Salud/EPS registrada",
                service.crear(empleadoId, input, usuarioActual()));
    }

    @PutMapping("/{id}")
    public ApiResponse<EmpleadoSaludEpsDto> editar(
            @PathVariable Long empleadoId,
            @PathVariable Long id,
            @Valid @RequestBody EmpleadoSaludEpsInputDto input) {
        return new ApiResponse<>("OK", "Cobertura Salud/EPS actualizada",
                service.editar(empleadoId, id, input, usuarioActual()));
    }

    @PostMapping("/{id}/cerrar")
    public ApiResponse<Void> cerrar(@PathVariable Long empleadoId, @PathVariable Long id) {
        service.cerrar(empleadoId, id, usuarioActual());
        return new ApiResponse<>("OK", "Cobertura cerrada", null);
    }

    @PostMapping("/{id}/anular")
    public ApiResponse<Void> anular(
            @PathVariable Long empleadoId,
            @PathVariable Long id,
            @Valid @RequestBody EmpleadoSaludEpsAnularInputDto req) {
        service.anular(empleadoId, id, req, usuarioActual());
        return new ApiResponse<>("OK",
                "Cobertura anulada. La información queda registrada en historial y auditoría.", null);
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SISTEMA";
    }
}
