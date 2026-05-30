package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.security.auth.SisrhSecurityExpressions;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.rrhh.dto.AprobacionPeriodoDto;
import com.indeci.rrhh.dto.PeriodoPlanillaDto;
import com.indeci.rrhh.dto.PeriodoPlanillaResponseDto;
import com.indeci.rrhh.service.PeriodoPlanillaService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/periodo-planilla")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class PeriodoPlanillaController {

    private final PeriodoPlanillaService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> guardar(
            @RequestBody PeriodoPlanillaDto dto) {

        service.guardar(dto);

        return new ApiResponse<>(
                "OK",
                "Periodo registrado",
                null);
    }

    @GetMapping
    public ApiResponse<List<PeriodoPlanillaResponseDto>>
    listar() {

        return new ApiResponse<>(
                "OK",
                "Lista periodos",
                service.listar());
    }

    @PutMapping("/enviar-revision/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_APPROVE)
    public ApiResponse<Void> enviarRevision(
            @PathVariable Long id) {

        service.enviarRevision(id);

        return new ApiResponse<>(
                "OK",
                "Periodo enviado a revisión",
                null);
    }

    @PutMapping("/aprobar/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_APPROVE)
    public ApiResponse<Void> aprobar(
            @PathVariable Long id,
            @RequestBody AprobacionPeriodoDto dto) {

        service.aprobar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Periodo aprobado",
                null);
    }

    @PutMapping("/cerrar/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_APPROVE)
    public ApiResponse<Void> cerrar(
            @PathVariable Long id) {

        service.cerrar(id);

        return new ApiResponse<>(
                "OK",
                "Periodo cerrado",
                null);
    }

    @PutMapping("/reabrir/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_APPROVE)
    public ApiResponse<Void> reabrir(
            @PathVariable Long id) {

        service.reabrir(id);

        return new ApiResponse<>(
                "OK",
                "Periodo reabierto",
                null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return new ApiResponse<>(
                "OK",
                "Periodo eliminado",
                null);
    }
}