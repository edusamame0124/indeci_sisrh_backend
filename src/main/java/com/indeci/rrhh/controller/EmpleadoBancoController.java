package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
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
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/banco")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class EmpleadoBancoController {

    private final EmpleadoBancoService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> guardar(@RequestBody EmpleadoBancoDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Cuenta registrada", null);
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoBancoResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Cuentas del empleado", service.listar(empleadoId));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(@PathVariable Long id, @RequestBody EmpleadoBancoDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Cuenta actualizada", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Cuenta desactivada", null);
    }
}
