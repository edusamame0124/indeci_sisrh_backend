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
import com.indeci.rrhh.dto.ConceptoPlanillaDto;
import com.indeci.rrhh.dto.ConceptoPlanillaResponseDto;
import com.indeci.rrhh.service.ConceptoPlanillaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/concepto-planilla")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class ConceptoPlanillaController {

    private final ConceptoPlanillaService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> guardar(@RequestBody ConceptoPlanillaDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Concepto registrado", null);
    }

    @GetMapping
    public ApiResponse<List<ConceptoPlanillaResponseDto>> listar() {
        return new ApiResponse<>("OK", "Lista conceptos", service.listar());
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody ConceptoPlanillaDto dto) {
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
