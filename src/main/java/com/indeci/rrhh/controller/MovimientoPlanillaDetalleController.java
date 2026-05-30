package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.security.auth.SisrhSecurityExpressions;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.MovimientoPlanillaDetalleResponseDto;
import com.indeci.rrhh.service.MovimientoPlanillaDetalleService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/planilla-detalle")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
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