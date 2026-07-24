package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.LegajoResumenDto;
import com.indeci.rrhh.service.LegajoResumenService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/legajo/resumen")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class LegajoResumenController {

    private final LegajoResumenService service;
    
    @GetMapping("/me")
    public ApiResponse<LegajoResumenDto> obtenerMiLegajo() {

        return new ApiResponse<>(
                "OK",
                "Legajo del empleado autenticado",
                service.obtenerMiLegajo()
        );
    }
    @GetMapping("/{personaId}")
    public ApiResponse<LegajoResumenDto>
    obtener(
            @PathVariable
            Long personaId) {

        return new ApiResponse<>(
                "OK",
                "Resumen de legajo",
                service.obtener(
                        personaId));
    }

}
