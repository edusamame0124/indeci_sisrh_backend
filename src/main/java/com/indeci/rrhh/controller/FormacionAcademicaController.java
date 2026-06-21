package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.FormacionAcademicaDto;
import com.indeci.rrhh.dto.FormacionAcademicaResponseDto;
import com.indeci.rrhh.service.FormacionAcademicaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/formacion-academica")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class FormacionAcademicaController {

    private final FormacionAcademicaService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody
            FormacionAcademicaDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Formación académica registrada",
                null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<FormacionAcademicaResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Formación académica",
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
                "Registro eliminado",
                null);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize(
            SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody
            FormacionAcademicaDto dto) {

        service.actualizar(
                id,
                dto);

        return new ApiResponse<>(
                "OK",
                "Registro actualizado",
                null);
    }
}