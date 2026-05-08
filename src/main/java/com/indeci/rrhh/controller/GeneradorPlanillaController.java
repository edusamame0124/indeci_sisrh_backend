package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ResumenPlanillaDto;
import com.indeci.rrhh.service.GeneradorPlanillaService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rrhh/generador-planilla")
@RequiredArgsConstructor
public class GeneradorPlanillaController {

    private final GeneradorPlanillaService service;

    @PostMapping("/{empleadoId}/{periodo}")
    public ApiResponse<Void> generar(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {

        service.generar(
                empleadoId,
                periodo);

        return new ApiResponse<>(
                "OK",
                "Planilla generada",
                null);
    }
    
    @PostMapping("/masivo/{periodo}")
    public ApiResponse<Void> generarMasivo(
            @PathVariable String periodo) {

        service.generarTodoPeriodo(periodo);

        return new ApiResponse<>(
                "OK",
                "Planilla masiva generada",
                null);
    }
    
    @GetMapping("/resumen/{empleadoId}/{periodo}")
    public ApiResponse<ResumenPlanillaDto>
    resumen(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {

        return new ApiResponse<>(
                "OK",
                "Resumen planilla",
                service.obtenerResumen(
                        empleadoId,
                        periodo));
    }
}