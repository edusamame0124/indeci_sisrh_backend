package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ConocimientoInformaticoDto;
import com.indeci.rrhh.dto.ConocimientoInformaticoResponseDto;
import com.indeci.rrhh.service.ConocimientoInformaticoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/conocimientos-informaticos")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class ConocimientoInformaticoController {

    private final ConocimientoInformaticoService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody
            ConocimientoInformaticoDto dto) {
    	System.out.println(dto);

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Conocimiento informático registrado",
                null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<
            List<ConocimientoInformaticoResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Conocimientos informáticos",
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
                "Conocimiento informático eliminado",
                null);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody ConocimientoInformaticoDto dto) {

        service.actualizar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Conocimiento informático actualizado",
                null);
    }
}