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
import com.indeci.rrhh.dto.EmpleadoPlanillaDto;
import com.indeci.rrhh.dto.EmpleadoPlanillaResponseDto;
import com.indeci.rrhh.service.EmpleadoPlanillaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/planilla")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class EmpleadoPlanillaController {

    private final EmpleadoPlanillaService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> guardar(@RequestBody EmpleadoPlanillaDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Planilla registrada", null);
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoPlanillaResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Planilla del empleado", service.listar(empleadoId));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> actualizar(@PathVariable Long id, @RequestBody EmpleadoPlanillaDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Planilla actualizada", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Planilla desactivada", null);
    }
}
