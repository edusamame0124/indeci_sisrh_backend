package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ConceptoTipoInternoDto;
import com.indeci.rrhh.service.ConceptoTipoInternoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * SPEC_CONCEPTOS_PLANILLA §13 — catálogo "Tipo de Concepto" (SISPER) de solo lectura.
 */
@RestController
@RequestMapping("/api/rrhh/concepto-tipo-interno")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class ConceptoTipoInternoController {

    private final ConceptoTipoInternoService service;

    @GetMapping
    public ApiResponse<List<ConceptoTipoInternoDto>> listar() {
        return new ApiResponse<>("OK", "Catálogo Tipo de Concepto", service.listar());
    }
}
