package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ExplicacionPlanillaDto;
import com.indeci.rrhh.service.ExplicacionPlanillaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * F3.1 — Endpoint que alimenta la Ficha 360 del Empleado.
 *
 * <p>Una sola llamada devuelve cabecera (identidad + régimen + banco) +
 * totales (6 KPI cards) + lista de líneas para el tab "Cálculo detallado"
 * con el botón "Explicar cálculo". Si no hay movimiento del empleado en el
 * período, la respuesta trae {@code aplica = false} y la UI muestra el empty
 * state con CTA "Generar planilla".</p>
 */
@RestController
@RequestMapping("/api/rrhh/empleado")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class ExplicacionPlanillaController {

    private final ExplicacionPlanillaService service;

    @GetMapping("/{empleadoId}/explicacion/{periodo}")
    public ApiResponse<ExplicacionPlanillaDto> explicar(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        return new ApiResponse<>(
                "OK",
                "Explicación de planilla para Ficha 360",
                service.explicar(empleadoId, periodo));
    }
}
