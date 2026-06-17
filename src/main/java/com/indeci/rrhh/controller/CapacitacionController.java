package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.CapacitacionDto;
import com.indeci.rrhh.dto.CapacitacionResponseDto;
import com.indeci.rrhh.service.CapacitacionService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/capacitaciones")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class CapacitacionController {

    private final CapacitacionService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody
            CapacitacionDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Capacitación registrada",
                null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<
            List<CapacitacionResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Capacitaciones",
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
                "Registro eliminado",
                null);
    }
}