package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.PrestamoDto;
import com.indeci.rrhh.dto.PrestamoResponseDto;
import com.indeci.rrhh.service.PrestamoService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Préstamos del empleado (SPEC §12.2 PANTALLA-08).
 * Base: {@code /api/rrhh/prestamo}.
 */
@RestController
@RequestMapping("/api/rrhh/prestamo")
@RequiredArgsConstructor
public class PrestamoController {

    private final PrestamoService service;

    @PostMapping
    public ApiResponse<Void> registrar(@RequestBody PrestamoDto dto) {
        service.registrar(dto);
        return new ApiResponse<>("OK", "Préstamo registrado", null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<PrestamoResponseDto>> listarPorEmpleado(
            @PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Préstamos del empleado",
                service.listarPorEmpleado(empleadoId));
    }

    @PutMapping("/{id}/pago")
    public ApiResponse<Void> registrarPago(@PathVariable Long id) {
        service.registrarPago(id);
        return new ApiResponse<>("OK", "Pago de cuota registrado", null);
    }
}
