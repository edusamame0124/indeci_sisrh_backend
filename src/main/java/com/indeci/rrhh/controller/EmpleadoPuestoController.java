package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EmpleadoPuestoDto;
import com.indeci.rrhh.dto.EmpleadoPuestoResponseDto;
import com.indeci.rrhh.service.EmpleadoPuestoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/puesto")
@RequiredArgsConstructor
public class EmpleadoPuestoController {

    private final EmpleadoPuestoService service;

    // CREAR CAMBIO
    @PostMapping
    public ApiResponse<Void> guardar(@RequestBody EmpleadoPuestoDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Cambio de puesto registrado", null);
    }

    // LISTAR HISTORIAL
    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoPuestoResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Historial laboral", service.listar(empleadoId));
    }

    // ELIMINAR (opcional)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Puesto desactivado", null);
    }
}