package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EmpleadoJornadaExcepcionDto;
import com.indeci.rrhh.dto.EmpleadoJornadaExcepcionResponseDto;
import com.indeci.rrhh.service.EmpleadoJornadaExcepcionService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Horario especial por trabajador (M04 Asistencia) — gestión exclusiva del
 * Módulo Vinculación (rol {@code VINCULACION}, permisos EMP_READ/EMP_WRITE).
 * El portal de autoservicio del empleado NO tiene acceso a este recurso.
 */
@RestController
@RequestMapping("/api/rrhh/empleado-jornada-excepcion")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class EmpleadoJornadaExcepcionController {

    private final EmpleadoJornadaExcepcionService service;

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<EmpleadoJornadaExcepcionResponseDto>> listarPorEmpleado(
            @PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Horarios especiales", service.listarPorEmpleado(empleadoId));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(@RequestBody EmpleadoJornadaExcepcionDto dto) {
        service.registrar(dto);
        return new ApiResponse<>("OK", "Horario especial registrado", null);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(@PathVariable Long id, @RequestBody EmpleadoJornadaExcepcionDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Horario especial actualizado", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Horario especial eliminado", null);
    }
}
