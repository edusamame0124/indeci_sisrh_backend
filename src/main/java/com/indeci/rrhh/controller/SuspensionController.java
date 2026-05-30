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
import com.indeci.rrhh.dto.SuspensionDto;
import com.indeci.rrhh.dto.SuspensionResponseDto;
import com.indeci.rrhh.entity.CatSuspensionSunat;
import com.indeci.rrhh.service.SuspensionService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/suspension")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class SuspensionController {

    private final SuspensionService service;

    @GetMapping("/catalogo")
    public ApiResponse<List<CatSuspensionSunat>> catalogo() {
        return new ApiResponse<>("OK", "Catálogo de tipos de suspensión SUNAT", service.catalogo());
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<SuspensionResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Suspensiones del empleado", service.listar(empleadoId));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<SuspensionResponseDto> crear(@RequestBody SuspensionDto dto) {
        return new ApiResponse<>("OK", "Suspensión registrada", service.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<SuspensionResponseDto> actualizar(
            @PathVariable Long id, @RequestBody SuspensionDto dto) {
        return new ApiResponse<>("OK", "Suspensión actualizada", service.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Suspensión anulada", null);
    }
}
