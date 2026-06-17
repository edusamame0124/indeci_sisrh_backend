package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.IdiomaDto;
import com.indeci.rrhh.dto.IdiomaResponseDto;
import com.indeci.rrhh.service.IdiomaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/idiomas")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class IdiomaController {

    private final IdiomaService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody
            IdiomaDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Idioma registrado",
                null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<
            List<IdiomaResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Idiomas",
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
                "Idioma eliminado",
                null);
    }
}