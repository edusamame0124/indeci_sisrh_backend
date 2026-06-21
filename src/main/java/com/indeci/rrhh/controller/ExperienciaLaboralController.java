package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ExperienciaLaboralDto;
import com.indeci.rrhh.dto.ExperienciaLaboralResponseDto;
import com.indeci.rrhh.service.ExperienciaLaboralService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/experiencias-laborales")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class ExperienciaLaboralController {

    private final ExperienciaLaboralService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody
            ExperienciaLaboralDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Experiencia laboral registrada",
                null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<
            List<ExperienciaLaboralResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Experiencia laboral",
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
                "Experiencia laboral eliminada",
                null);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody ExperienciaLaboralDto dto) {

        service.actualizar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Experiencia laboral actualizada",
                null);
    }
    
    
}