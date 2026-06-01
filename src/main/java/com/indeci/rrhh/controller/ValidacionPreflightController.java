package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.PreflightValidacionDto;
import com.indeci.rrhh.service.ValidacionPreflightService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * F3.3 — Centro de Validaciones.
 *
 * <p>Devuelve los hallazgos detectables antes de correr el motor de planilla.
 * Sólo lectura; no muta datos.</p>
 */
@RestController
@RequestMapping("/api/rrhh/validaciones")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class ValidacionPreflightController {

    private final ValidacionPreflightService service;

    @GetMapping("/preflight")
    public ApiResponse<PreflightValidacionDto> preflight(@RequestParam String periodo) {
        return new ApiResponse<>(
                "OK",
                "Hallazgos previos a la generación de planilla",
                service.evaluar(periodo));
    }
}
