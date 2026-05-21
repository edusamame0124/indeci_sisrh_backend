package com.indeci.rrhh.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EmpleadoFlowStatusDto;
import com.indeci.rrhh.service.EmpleadoFlowStatusService;

import lombok.RequiredArgsConstructor;

/**
 * Spec 012 / C3 (BKD-006) — Endpoint agregado del flujo de empleado.
 *
 * <p>Una sola llamada devuelve qué pasos de configuración (puesto, banco,
 * pensión, planilla, conceptos) ya tienen registros, en lugar de los 5 GET
 * paralelos que el frontend hacía recurso por recurso.
 */
@RestController
@RequestMapping("/api/rrhh/empleado")
@RequiredArgsConstructor
public class EmpleadoFlowController {

    private final EmpleadoFlowStatusService service;

    @GetMapping("/{empleadoId}/flow-status")
    public ApiResponse<EmpleadoFlowStatusDto> flowStatus(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Estado del flujo de empleado", service.obtener(empleadoId));
    }
}
