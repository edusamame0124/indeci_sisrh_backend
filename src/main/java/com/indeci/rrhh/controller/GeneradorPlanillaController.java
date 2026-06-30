package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.GeneracionMasivaResultDto;
import com.indeci.rrhh.dto.GenerarPlanillaCabeceraDto;
import com.indeci.rrhh.dto.ResumenPlanillaDto;
import com.indeci.rrhh.service.GeneradorPlanillaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/generador-planilla")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class GeneradorPlanillaController {

    private final GeneradorPlanillaService service;

    @PostMapping("/{empleadoId}/{periodo}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> generar(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {

        service.generar(empleadoId, periodo);

        return new ApiResponse<>("OK", "Planilla generada", null);
    }

    @PostMapping("/masivo")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<GeneracionMasivaResultDto> generarMasivo(@RequestBody GenerarPlanillaCabeceraDto request) {

        GeneracionMasivaResultDto resultado = service.generarTodoPeriodo(request);

        return new ApiResponse<>(
                "OK",
                "Generación masiva ejecutada: " + resultado.getExitosos()
                        + " de " + resultado.getTotal(),
                resultado);
    }

    @GetMapping("/resumen/{empleadoId}/{periodo}")
    public ApiResponse<ResumenPlanillaDto> resumen(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {

        return new ApiResponse<>(
                "OK",
                "Resumen planilla",
                service.obtenerResumen(empleadoId, periodo));
    }
}
