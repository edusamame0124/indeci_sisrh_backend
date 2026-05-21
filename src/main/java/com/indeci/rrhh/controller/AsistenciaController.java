package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.AsistenciaGuardarDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.service.AsistenciaService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

/**
 * Módulo M04 — Asistencia (SPEC §12.2 PANTALLA-02).
 * Base: {@code /api/rrhh/asistencia}.
 */
@RestController
@RequestMapping("/api/rrhh/asistencia")
@RequiredArgsConstructor
public class AsistenciaController {

    private final AsistenciaService service;

    /** Asistencia de un empleado en un período (calendario nuevo si no existe). */
    @GetMapping("/{empleadoId}/{periodo}")
    public ApiResponse<AsistenciaResponseDto> obtener(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {

        return new ApiResponse<>(
                "OK",
                "Asistencia del período",
                service.obtener(empleadoId, periodo));
    }

    /** Guarda (UPSERT) la asistencia del período y recalcula descuentos. */
    @PostMapping
    public ApiResponse<Void> guardar(
            @RequestBody AsistenciaGuardarDto dto) {

        service.guardar(dto);

        return new ApiResponse<>(
                "OK",
                "Asistencia registrada",
                null);
    }
}
