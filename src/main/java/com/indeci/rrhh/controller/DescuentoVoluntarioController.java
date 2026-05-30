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
import com.indeci.rrhh.dto.DescuentoVoluntarioDto;
import com.indeci.rrhh.dto.DescuentoVoluntarioResponseDto;
import com.indeci.rrhh.service.DescuentoVoluntarioService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/descuento-voluntario")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class DescuentoVoluntarioController {

    private final DescuentoVoluntarioService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> guardar(@RequestBody DescuentoVoluntarioDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Descuento voluntario registrado", null);
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<DescuentoVoluntarioResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Descuentos voluntarios del empleado",
                service.listar(empleadoId));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id, @RequestBody DescuentoVoluntarioDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Descuento voluntario actualizado", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Descuento voluntario desactivado", null);
    }
}
