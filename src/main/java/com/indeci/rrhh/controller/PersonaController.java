package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.security.auth.SisrhSecurityExpressions;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.rrhh.dto.PersonaEmpleadoDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.service.PersonaService;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rrhh")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class PersonaController {

    private final PersonaService personaService;

    // CREAR
    @PostMapping("/persona")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> guardar(@RequestBody PersonaEmpleadoDto dto) {
        personaService.guardar(dto);
        return new ApiResponse<>("OK", "Registrado correctamente", null);
    }

    // LISTAR
    @GetMapping("/persona")
    public ApiResponse<List<PersonaEmpleadoResponseDto>> listar() {
        return new ApiResponse<>("OK", "Listado correcto", personaService.listar());
    }

    // DETALLE
    @GetMapping("/persona/{id}")
    public ApiResponse<PersonaEmpleadoResponseDto> obtener(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Detalle correcto", personaService.obtenerPorId(id));
    }

    // ACTUALIZAR
    @PutMapping("/persona/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody PersonaEmpleadoDto dto
    ) {
        personaService.actualizar(id, dto);
        return new ApiResponse<>("OK", "Actualizado correctamente", null);
    }

    // ELIMINAR
    @DeleteMapping("/persona/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        personaService.eliminar(id);
        return new ApiResponse<>("OK", "Eliminado correctamente", null);
    }
}