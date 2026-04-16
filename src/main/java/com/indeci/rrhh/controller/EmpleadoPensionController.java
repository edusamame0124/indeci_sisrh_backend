package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EmpleadoPensionDto;
import com.indeci.rrhh.dto.EmpleadoPensionResponseDto;
import com.indeci.rrhh.service.EmpleadoPensionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/pension")
@RequiredArgsConstructor
public class EmpleadoPensionController {

    private final EmpleadoPensionService service;

    // CREAR
    @PostMapping
    public ApiResponse<Void> guardar(@RequestBody EmpleadoPensionDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Pensión registrada", null);
    }

    // LISTAR
    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoPensionResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Pensión del empleado", service.listar(empleadoId));
    }

    // ACTUALIZAR
    @PutMapping("/{id}")
    public ApiResponse<Void> actualizar(@PathVariable Long id, @RequestBody EmpleadoPensionDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Pensión actualizada", null);
    }

    // ELIMINAR
    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Pensión desactivada", null);
    }
}