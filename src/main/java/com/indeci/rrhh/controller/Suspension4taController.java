package com.indeci.rrhh.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.Suspension4taRequestDto;
import com.indeci.rrhh.dto.Suspension4taResponseDto;
import com.indeci.rrhh.dto.ir4ta.Ir4taControlAnualDto;
import com.indeci.rrhh.dto.ir4ta.Ir4taReinicioInputDto;
import com.indeci.rrhh.service.Ir4taControlAnualService;
import com.indeci.rrhh.service.Suspension4taService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * FASE 1 — Constancias de suspensión de retención de 4ta categoría (CAS).
 * Dato tributario del empleado, separado de AFP/ONP (pensión).
 */
@RestController
@RequestMapping("/api/rrhh/suspension-4ta")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class Suspension4taController {

    private final Suspension4taService service;
    private final Ir4taControlAnualService controlAnualService;

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<Suspension4taResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Constancias de suspensión 4ta del empleado",
                service.listarPorEmpleado(empleadoId));
    }

    // ── Control anual del tope de suspensión (Wireframe B) ───────────────────

    /** Vista de solo lectura del control anual (acumulado, tope, alertas). */
    @GetMapping("/{empleadoId}/control-anual")
    public ApiResponse<Ir4taControlAnualDto> controlAnual(
            @PathVariable Long empleadoId,
            @RequestParam int anio) {
        return new ApiResponse<>("OK", "Control anual de suspensión 4ta",
                controlAnualService.obtenerControl(empleadoId, anio));
    }

    /** Flag manual de RR.HH.: qué tope aplica al trabajador (GENERAL_CAS|DIRECTOR_SIMILAR). */
    @PutMapping("/{empleadoId}/control-anual/tipo-tope")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Ir4taControlAnualDto> definirTipoTope(
            @PathVariable Long empleadoId,
            @RequestParam int anio,
            @RequestParam String tipoTope) {
        return new ApiResponse<>("OK", "Tipo de tope actualizado",
                controlAnualService.definirTipoTope(empleadoId, anio, tipoTope, usuarioActual()));
    }

    /** Confirmación de reinicio de retención tras superar el tope (RR.HH. — EMP_WRITE). */
    @PostMapping("/{empleadoId}/control-anual/confirmar-reinicio")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Ir4taControlAnualDto> confirmarReinicio(
            @PathVariable Long empleadoId,
            @Valid @RequestBody Ir4taReinicioInputDto input) {
        return new ApiResponse<>("OK", "Reinicio de retención confirmado",
                controlAnualService.confirmarReinicio(empleadoId, input, usuarioActual()));
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SISTEMA";
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Suspension4taResponseDto> crear(@RequestBody Suspension4taRequestDto dto) {
        return new ApiResponse<>("OK", "Constancia de suspensión 4ta registrada", service.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Suspension4taResponseDto> actualizar(
            @PathVariable Long id, @RequestBody Suspension4taRequestDto dto) {
        return new ApiResponse<>("OK", "Constancia de suspensión 4ta actualizada",
                service.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> anular(@PathVariable Long id) {
        service.anular(id);
        return new ApiResponse<>("OK", "Constancia de suspensión 4ta anulada", null);
    }
}
