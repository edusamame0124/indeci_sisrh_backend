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
import com.indeci.rrhh.dto.PlanillaTipoDto;
import com.indeci.rrhh.service.PlanillaTipoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — catálogo administrable de tipos de planilla.
 *
 * <p>Listar (PLA_READ) + alta/edición/baja lógica (PLA_WRITE) para administración.</p>
 */
@RestController
@RequestMapping("/api/rrhh/planilla-tipo")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class PlanillaTipoController {

    private final PlanillaTipoService service;

    @GetMapping
    public ApiResponse<List<PlanillaTipoDto>> listar() {
        return new ApiResponse<>("OK", "Catálogo de tipos de planilla", service.listar());
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<PlanillaTipoDto> crear(@RequestBody PlanillaTipoDto dto) {
        return new ApiResponse<>("OK", "Tipo de planilla creado", service.crear(dto));
    }

    @PutMapping("/{codigo}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<PlanillaTipoDto> actualizar(
            @PathVariable String codigo,
            @RequestBody PlanillaTipoDto dto) {
        return new ApiResponse<>("OK", "Tipo de planilla actualizado",
                service.actualizar(codigo, dto));
    }

    @DeleteMapping("/{codigo}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable String codigo) {
        service.eliminar(codigo);
        return new ApiResponse<>("OK", "Tipo de planilla eliminado", null);
    }
}
