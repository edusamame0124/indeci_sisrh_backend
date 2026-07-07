package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.cts.CtsCalcularRequest;
import com.indeci.rrhh.dto.cts.CtsCandidatoDto;
import com.indeci.rrhh.dto.cts.CtsDesgloseDto;
import com.indeci.rrhh.dto.cts.CtsLiquidacionResponseDto;
import com.indeci.rrhh.service.cts.CtsCalculadorService;
import com.indeci.rrhh.service.cts.CtsLiquidacionService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Feature 016 — API del módulo Liquidación de CTS Trunca
 * ({@code /api/rrhh/liquidaciones/cts}). RBAC granular PLA_CTS_*.
 *
 * <p>Base normativa: Ley N.° 30057 Art. 34 + D.S. 040-2014-PCM (SERVIR) y
 * D.Leg. 276 (Carrera Administrativa). CAS 1057 bloqueado por norma.</p>
 */
@RestController
@RequestMapping("/api/rrhh/liquidaciones/cts")
@RequiredArgsConstructor
public class LiquidacionCtsController {

    private final CtsCalculadorService calculadorService;
    private final CtsLiquidacionService liquidacionService;

    @GetMapping("/candidatos")
    @PreAuthorize(SisrhSecurityExpressions.PLA_CTS_READ)
    public ApiResponse<List<CtsCandidatoDto>> candidatos(
            @RequestParam String periodo,
            @RequestParam Long regimenLaboralId) {
        return new ApiResponse<>("OK", "Cesantes aptos para liquidación",
                liquidacionService.listarCandidatos(periodo, regimenLaboralId));
    }

    @PostMapping("/calcular")
    @PreAuthorize(SisrhSecurityExpressions.PLA_CTS_WRITE)
    public ApiResponse<CtsLiquidacionResponseDto> calcular(
            @Valid @RequestBody CtsCalcularRequest req) {
        return new ApiResponse<>("OK", "Liquidación de CTS calculada",
                calculadorService.calcular(req.empleadoId(), req.empleadoPlanillaId(), req.periodo()));
    }

    @GetMapping("/{id}/desglose")
    @PreAuthorize(SisrhSecurityExpressions.PLA_CTS_READ)
    public ApiResponse<CtsDesgloseDto> desglose(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Desglose y trazabilidad del cálculo",
                liquidacionService.obtenerDesglose(id));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_CTS_APPROVE)
    public ApiResponse<CtsLiquidacionResponseDto> aprobar(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Liquidación aprobada y cerrada",
                liquidacionService.aprobar(id));
    }
}
