package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.MedidaDisciplinariaDto;
import com.indeci.rrhh.dto.MedidaDisciplinariaResponseDto;
import com.indeci.rrhh.service.MedidaDisciplinariaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/medidas-disciplinarias")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class MedidaDisciplinariaController {

    private final MedidaDisciplinariaService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody
            MedidaDisciplinariaDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Medida disciplinaria registrada",
                null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<
            List<MedidaDisciplinariaResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Medidas disciplinarias",
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
                "Medida disciplinaria eliminada",
                null);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody MedidaDisciplinariaDto dto) {

        service.actualizar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Medida disciplinaria actualizada",
                null);
    }
}