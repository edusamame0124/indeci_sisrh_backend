package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EstimacionNetoDto;
import com.indeci.rrhh.dto.EstimacionNetoRequestDto;
import com.indeci.rrhh.service.EstimacionNetoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * Spec 013 / C1 — Preview de neto (solo lectura estimada, no persiste).
 */
@RestController
@RequestMapping("/api/rrhh/empleados")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class EstimacionNetoController {

    private final EstimacionNetoService service;

    @PostMapping("/{id}/estimar-neto")
    public ApiResponse<EstimacionNetoDto> estimarNeto(
            @PathVariable Long id,
            @RequestBody EstimacionNetoRequestDto request) {

        EstimacionNetoDto dto = service.estimarNeto(
                id, request.getConceptoId(), request.getMonto());

        return new ApiResponse<>("OK", "Estimación de neto", dto);
    }
}
