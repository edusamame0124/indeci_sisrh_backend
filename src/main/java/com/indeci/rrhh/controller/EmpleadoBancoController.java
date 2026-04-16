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
import com.indeci.rrhh.dto.EmpleadoBancoDto;
import com.indeci.rrhh.dto.EmpleadoBancoResponseDto;
import com.indeci.rrhh.service.EmpleadoBancoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/banco")
@RequiredArgsConstructor
public class EmpleadoBancoController {

    private final EmpleadoBancoService service;

    // CREAR
    @PostMapping
    public ApiResponse<Void> guardar(@RequestBody EmpleadoBancoDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Cuenta registrada", null);
    }

    // LISTAR
    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoBancoResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Cuentas del empleado", service.listar(empleadoId));
    }

    // ACTUALIZAR
    @PutMapping("/{id}")
    public ApiResponse<Void> actualizar(@PathVariable Long id, @RequestBody EmpleadoBancoDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Cuenta actualizada", null);
    }

    // ELIMINAR
    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Cuenta desactivada", null);
    }
}