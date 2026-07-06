package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.AguinaldoRequest;
import com.indeci.rrhh.dto.AguinaldoResultDto;
import com.indeci.rrhh.service.AguinaldoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * Track B — AGUINALDO (proceso aparte, tipo de planilla AGUINALDO).
 * SERVIR 100% / CAS %manual (piso) / 276 fijo. El % de CAS viaja en el request.
 */
@RestController
@RequestMapping("/api/rrhh/aguinaldo")
@RequiredArgsConstructor
public class AguinaldoController {

    private final AguinaldoService service;

    @PostMapping("/generar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<AguinaldoResultDto> generar(@RequestBody AguinaldoRequest request) {
        return new ApiResponse<>("OK", "Aguinaldo generado", service.generar(request));
    }
}
