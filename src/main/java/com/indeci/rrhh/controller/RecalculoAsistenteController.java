package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.RecalculoCriterioDto;
import com.indeci.rrhh.dto.RecalculoPreviewDto;
import com.indeci.rrhh.dto.RecalculoResultadoDto;
import com.indeci.rrhh.service.RecalculoAsistenteService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * F3.4 — Asistente de Recálculo.
 *
 * <p>Dos endpoints: {@code /preview} (PLA_READ, solo lectura) y
 * {@code /ejecutar} (PLA_WRITE, dispara el motor por cada empleado del
 * alcance). El período se pasa por query string y el criterio por body.</p>
 */
@RestController
@RequestMapping("/api/rrhh/recalculo")
@RequiredArgsConstructor
public class RecalculoAsistenteController {

    private final RecalculoAsistenteService service;

    @PostMapping("/preview")
    @PreAuthorize(SisrhSecurityExpressions.PLA_READ)
    public ApiResponse<RecalculoPreviewDto> preview(
            @RequestParam String periodo,
            @RequestBody RecalculoCriterioDto criterio) {
        return new ApiResponse<>(
                "OK",
                "Alcance del recálculo",
                service.preview(periodo, criterio));
    }

    @PostMapping("/ejecutar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<RecalculoResultadoDto> ejecutar(
            @RequestParam String periodo,
            @RequestBody RecalculoCriterioDto criterio) {
        return new ApiResponse<>(
                "OK",
                "Recálculo ejecutado",
                service.ejecutar(periodo, criterio));
    }
}
