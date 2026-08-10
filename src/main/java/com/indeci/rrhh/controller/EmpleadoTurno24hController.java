package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EmpleadoTurno24hDto;
import com.indeci.rrhh.dto.EmpleadoTurno24hResponseDto;
import com.indeci.rrhh.service.EmpleadoTurno24hService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Turno continuo 24h por trabajador (M04 Asistencia — guardia COEN), pestaña
 * propia de Gestión de Asistencia. RBAC compartido con Jornada y tolerancias:
 * {@link SisrhSecurityExpressions#JORNADA_READ} / {@link SisrhSecurityExpressions#JORNADA_WRITE}
 * (ASISTENCIA o PLANILLA).
 */
@RestController
@RequestMapping("/api/rrhh/empleado-turno-24h")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.JORNADA_READ)
public class EmpleadoTurno24hController {

    private final EmpleadoTurno24hService service;

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<EmpleadoTurno24hResponseDto>> listarPorEmpleado(
            @PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Turnos 24h", service.listarPorEmpleado(empleadoId));
    }

    @GetMapping
    public ApiResponse<List<EmpleadoTurno24hResponseDto>> listarTodosVigentes() {
        return new ApiResponse<>("OK", "Turnos 24h vigentes", service.listarTodosVigentes());
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.JORNADA_WRITE)
    public ApiResponse<Void> registrar(@RequestBody EmpleadoTurno24hDto dto) {
        service.registrar(dto);
        return new ApiResponse<>("OK", "Turno 24h registrado", null);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.JORNADA_WRITE)
    public ApiResponse<Void> actualizar(@PathVariable Long id, @RequestBody EmpleadoTurno24hDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Turno 24h actualizado", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.JORNADA_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Turno 24h eliminado", null);
    }
}
