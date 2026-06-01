package com.indeci.rrhh.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EncargaturaDto;
import com.indeci.rrhh.dto.EncargaturaResponseDto;
import com.indeci.rrhh.service.EmpleadoEncargaturaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * F5.2 — Gestión de encargaturas.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET    /api/rrhh/encargatura?estado=ACTIVO|CULMINADO|TODOS}</li>
 *   <li>{@code POST   /api/rrhh/encargatura}</li>
 *   <li>{@code PUT    /api/rrhh/encargatura/{id}}</li>
 *   <li>{@code PUT    /api/rrhh/encargatura/{id}/cerrar?fechaFin=YYYY-MM-DD}</li>
 *   <li>{@code DELETE /api/rrhh/encargatura/{id}}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rrhh/encargatura")
@RequiredArgsConstructor
public class EmpleadoEncargaturaController {

    private final EmpleadoEncargaturaService service;

    @GetMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_READ)
    public ApiResponse<List<EncargaturaResponseDto>> listar(
            @RequestParam(required = false) String estado) {
        return new ApiResponse<>(
                "OK",
                "Listado de encargaturas",
                service.listar(estado));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<EncargaturaResponseDto> crear(@RequestBody EncargaturaDto dto) {
        return new ApiResponse<>(
                "OK",
                "Encargatura registrada",
                service.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<EncargaturaResponseDto> actualizar(
            @PathVariable Long id, @RequestBody EncargaturaDto dto) {
        return new ApiResponse<>(
                "OK",
                "Encargatura actualizada",
                service.actualizar(id, dto));
    }

    @PutMapping("/{id}/cerrar")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<EncargaturaResponseDto> cerrar(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return new ApiResponse<>(
                "OK",
                "Encargatura culminada",
                service.cerrar(id, fechaFin));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Encargatura eliminada", null);
    }
}
