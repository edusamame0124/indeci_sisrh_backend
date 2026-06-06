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
import com.indeci.rrhh.dto.ConceptosAsignablesDto;
import com.indeci.rrhh.dto.EmpleadoConceptoDto;
import com.indeci.rrhh.dto.EmpleadoConceptoResponseDto;
import com.indeci.rrhh.service.EmpleadoConceptoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * Spec 013 / C1 — Asignación manual de conceptos de planilla al empleado.
 * Permisos {@code PLA_*}: analistas de planilla pueden escribir sin {@code EMP_WRITE}.
 */
@RestController
@RequestMapping("/api/rrhh/empleado-concepto")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class EmpleadoConceptoController {

    private final EmpleadoConceptoService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> guardar(@RequestBody EmpleadoConceptoDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Concepto asignado", null);
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoConceptoResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Conceptos empleado", service.listarEmpleado(empleadoId));
    }

    /** Conceptos asignables al empleado, filtrados por su régimen (mejora 2026-06-03). */
    @GetMapping("/{empleadoId}/asignables")
    public ApiResponse<ConceptosAsignablesDto> asignables(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Conceptos asignables", service.listarAsignables(empleadoId));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody EmpleadoConceptoDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Concepto actualizado", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Concepto eliminado", null);
    }
}
