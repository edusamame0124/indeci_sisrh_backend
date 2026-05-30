package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.MovimientoPlanillaResponseDto;
import com.indeci.rrhh.dto.ResumenMetaDto;
import com.indeci.rrhh.service.MovimientoPlanillaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/movimiento-planilla")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class MovimientoPlanillaController {

    private final MovimientoPlanillaService service;

    @GetMapping("/{empleadoId}/{periodo}")
    public ApiResponse<MovimientoPlanillaResponseDto> obtenerEmpleado(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        return new ApiResponse<>("OK", "Planilla empleado",
                service.obtenerEmpleado(empleadoId, periodo));
    }

    @GetMapping("/periodo/{periodo}")
    public ApiResponse<List<MovimientoPlanillaResponseDto>> listarPeriodo(
            @PathVariable String periodo) {
        return new ApiResponse<>("OK", "Planillas periodo", service.listarPeriodo(periodo));
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<MovimientoPlanillaResponseDto>> listarPorEmpleado(
            @PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Historial de planillas del empleado",
                service.listarPorEmpleado(empleadoId));
    }

    @GetMapping("/resumen-por-meta/{periodo}")
    public ApiResponse<List<ResumenMetaDto>> resumenPorMeta(@PathVariable String periodo) {
        return new ApiResponse<>("OK", "Resumen por meta", service.resumenPorMeta(periodo));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Planilla eliminada", null);
    }

    @PutMapping("/{id}/estado/{estado}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> cambiarEstado(
            @PathVariable Long id,
            @PathVariable String estado) {
        service.cambiarEstado(id, estado);
        return new ApiResponse<>("OK", "Estado actualizado", null);
    }
}
