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
import com.indeci.rrhh.dto.EmpleadoPuestoDto;
import com.indeci.rrhh.dto.EmpleadoPuestoResponseDto;
import com.indeci.rrhh.service.EmpleadoPuestoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/puesto")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class EmpleadoPuestoController {

    private final EmpleadoPuestoService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> guardar(@RequestBody EmpleadoPuestoDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Cambio de puesto registrado", null);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody EmpleadoPuestoDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Puesto actualizado", null);
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoPuestoResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Historial laboral", service.listar(empleadoId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Puesto desactivado", null);
    }
}
