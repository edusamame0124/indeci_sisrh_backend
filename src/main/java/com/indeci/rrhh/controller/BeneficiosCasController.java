package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.BeneficioCasCalculadoDto;
import com.indeci.rrhh.service.BeneficiosCasService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * F2bis — Endpoint REST para consultar/preview los Beneficios CAS 2026
 * (aguinaldo + bonificación extraordinaria) sin grabar nada en planilla.
 *
 * <p>Utilizado por la UI (F3 Portal Empleado / Ficha 360) para mostrar
 * "Aguinaldo proyectado julio 2026" antes de generar la planilla. La
 * generación real se conectará al motor cuando RRHH entregue los
 * CODIGO_MEF de los conceptos.</p>
 *
 * <p>Rutas:</p>
 * <ul>
 *   <li>{@code GET /api/rrhh/beneficios-cas/preview/{periodo}/{regimen}}
 *       — preview del cálculo.</li>
 *   <li>{@code GET /api/rrhh/beneficios-cas/enabled} — estado del feature flag.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rrhh/beneficios-cas")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class BeneficiosCasController {

    private final BeneficiosCasService service;

    @GetMapping("/preview/{periodo}/{regimen}")
    public ApiResponse<BeneficioCasCalculadoDto> preview(
            @PathVariable String periodo,
            @PathVariable String regimen) {
        return new ApiResponse<>(
                "OK",
                "Preview de beneficios CAS",
                service.calcular(periodo, regimen));
    }

    @GetMapping("/enabled")
    public ApiResponse<Boolean> enabled() {
        return new ApiResponse<>(
                "OK",
                "Feature flag de beneficios CAS 2026",
                service.isEnabled());
    }

    /**
     * Track B F4 — Gratificación CAS que el motor evaluará automáticamente en el
     * período (read-only para la pantalla de generación). {@code null} si no aplica.
     */
    @GetMapping("/gratificacion-aplicable/{periodo}")
    public ApiResponse<String> gratificacionAplicable(@PathVariable String periodo) {
        return new ApiResponse<>(
                "OK",
                "Gratificación CAS aplicable al período",
                service.gratificacionAplicableEtiqueta(periodo).orElse(null));
    }
}
