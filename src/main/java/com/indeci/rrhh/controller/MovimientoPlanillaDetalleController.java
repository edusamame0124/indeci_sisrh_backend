package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.MovimientoPlanillaDetalleResponseDto;
import com.indeci.rrhh.service.MovimientoPlanillaDetalleService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/planilla-detalle")
@RequiredArgsConstructor
public class MovimientoPlanillaDetalleController {

    private final MovimientoPlanillaDetalleService
            service;

    // ==========================================
    // LISTAR DETALLE
    // ==========================================

    @GetMapping("/{empleadoId}/{periodo}")
    public ApiResponse<List<MovimientoPlanillaDetalleResponseDto>>
    listarDetalle(@PathVariable Long empleadoId,
                  @PathVariable String periodo) {

        return new ApiResponse<>(
                "OK",
                "Detalle planilla",
                service.listarDetalle(
                        empleadoId,
                        periodo));
    }
}