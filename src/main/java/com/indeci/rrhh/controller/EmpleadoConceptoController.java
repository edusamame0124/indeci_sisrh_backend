package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EmpleadoConceptoDto;
import com.indeci.rrhh.dto.EmpleadoConceptoResponseDto;
import com.indeci.rrhh.service.EmpleadoConceptoService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/empleado-concepto")
@RequiredArgsConstructor
public class EmpleadoConceptoController {

    private final EmpleadoConceptoService service;

    @PostMapping
    public ApiResponse<Void> guardar(
            @RequestBody EmpleadoConceptoDto dto) {

        service.guardar(dto);

        return new ApiResponse<>(
                "OK",
                "Concepto asignado",
                null);
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoConceptoResponseDto>>
    listar(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Conceptos empleado",
                service.listarEmpleado(
                        empleadoId));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody EmpleadoConceptoDto dto) {

        service.actualizar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Concepto actualizado",
                null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return new ApiResponse<>(
                "OK",
                "Concepto eliminado",
                null);
    }
}