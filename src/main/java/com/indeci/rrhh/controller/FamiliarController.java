package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.FamiliarDto;
import com.indeci.rrhh.dto.FamiliarResponseDto;
import com.indeci.rrhh.service.FamiliarService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/familiares")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class FamiliarController {

    private final FamiliarService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody FamiliarDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Familiar registrado",
                null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<FamiliarResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Familiares del empleado",
                service.listarPorEmpleado(
                        empleadoId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return new ApiResponse<>(
                "OK",
                "Familiar eliminado",
                null);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody FamiliarDto dto) {

        service.actualizar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Familiar actualizado",
                null);
    }
}