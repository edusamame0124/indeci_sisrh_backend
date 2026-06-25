package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ConceptoRtpsDto;
import com.indeci.rrhh.service.ConceptoRtpsService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * SPEC_CONCEPTOS_PLANILLA P1 — catálogo RTPS (PDT 601) de solo lectura.
 */
@RestController
@RequestMapping("/api/rrhh/concepto-rtps")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class ConceptoRtpsController {

    private final ConceptoRtpsService service;

    @GetMapping
    public ApiResponse<List<ConceptoRtpsDto>> listar() {
        return new ApiResponse<>("OK", "Catálogo RTPS", service.listar());
    }
}
