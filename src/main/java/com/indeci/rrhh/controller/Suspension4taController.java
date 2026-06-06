package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.Suspension4taRequestDto;
import com.indeci.rrhh.dto.Suspension4taResponseDto;
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

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<Suspension4taResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Constancias de suspensión 4ta del empleado",
                service.listarPorEmpleado(empleadoId));
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
