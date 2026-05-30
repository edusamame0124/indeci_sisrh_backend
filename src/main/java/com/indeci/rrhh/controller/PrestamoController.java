package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.PrestamoDto;
import com.indeci.rrhh.dto.PrestamoResponseDto;
import com.indeci.rrhh.service.PrestamoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/prestamo")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class PrestamoController {

    private final PrestamoService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(@RequestBody PrestamoDto dto) {
        service.registrar(dto);
        return new ApiResponse<>("OK", "Préstamo registrado", null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<PrestamoResponseDto>> listarPorEmpleado(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Préstamos del empleado",
                service.listarPorEmpleado(empleadoId));
    }

    @PutMapping("/{id}/pago")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrarPago(@PathVariable Long id) {
        service.registrarPago(id);
        return new ApiResponse<>("OK", "Pago de cuota registrado", null);
    }
}
