package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ReporteEvolucionDto;
import com.indeci.rrhh.dto.ReporteRegimenDto;
import com.indeci.rrhh.dto.ReporteTopConceptosDto;
import com.indeci.rrhh.service.ReporteConsolidadoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * F3.5 — Tablero Consolidado.
 *
 * <p>Endpoints solo lectura sobre datos ya grabados:</p>
 * <ul>
 *   <li>{@code /evolucion}    — totales por período en una ventana móvil.</li>
 *   <li>{@code /regimen}      — distribución de un período por régimen.</li>
 *   <li>{@code /top-conceptos}— ranking de conceptos del período.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rrhh/reportes/consolidado")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.RPT_READ)
public class ReporteConsolidadoController {

    private final ReporteConsolidadoService service;

    @GetMapping("/evolucion")
    public ApiResponse<ReporteEvolucionDto> evolucion(
            @RequestParam String periodoBase,
            @RequestParam(defaultValue = "6") int meses) {
        return new ApiResponse<>(
                "OK",
                "Evolución multi-período",
                service.evolucion(periodoBase, meses));
    }

    @GetMapping("/regimen")
    public ApiResponse<ReporteRegimenDto> regimen(@RequestParam String periodo) {
        return new ApiResponse<>(
                "OK",
                "Distribución por régimen laboral",
                service.distribucionRegimen(periodo));
    }

    @GetMapping("/top-conceptos")
    public ApiResponse<ReporteTopConceptosDto> topConceptos(
            @RequestParam String periodo,
            @RequestParam(defaultValue = "10") int limite) {
        return new ApiResponse<>(
                "OK",
                "Top conceptos del período",
                service.topConceptos(periodo, limite));
    }
}
