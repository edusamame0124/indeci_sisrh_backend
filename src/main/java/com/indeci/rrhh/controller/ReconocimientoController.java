package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ReconocimientoDto;
import com.indeci.rrhh.dto.ReconocimientoResponseDto;
import com.indeci.rrhh.service.ReconocimientoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/reconocimientos")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class ReconocimientoController {

    private final ReconocimientoService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody
            ReconocimientoDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Reconocimiento registrado",
                null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<
            List<ReconocimientoResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Reconocimientos",
                service.listarPorEmpleado(
                        empleadoId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return new ApiResponse<>(
                "OK",
                "Reconocimiento eliminado",
                null);
    }
}