package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.MovimientoPlanillaResponseDto;
import com.indeci.rrhh.dto.ResumenMetaDto;
import com.indeci.rrhh.service.MovimientoPlanillaService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/movimiento-planilla")
@RequiredArgsConstructor
public class MovimientoPlanillaController {

    private final MovimientoPlanillaService service;

    // ==========================================
    // OBTENER PLANILLA EMPLEADO
    // ==========================================

    @GetMapping("/{empleadoId}/{periodo}")
    public ApiResponse<MovimientoPlanillaResponseDto>
    obtenerEmpleado(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {

        return new ApiResponse<>(
                "OK",
                "Planilla empleado",
                service.obtenerEmpleado(
                        empleadoId,
                        periodo));
    }

    // ==========================================
    // LISTAR PLANILLAS DEL PERIODO
    // ==========================================

    @GetMapping("/periodo/{periodo}")
    public ApiResponse<List<MovimientoPlanillaResponseDto>>
    listarPeriodo(
            @PathVariable String periodo) {

        return new ApiResponse<>(
                "OK",
                "Planillas periodo",
                service.listarPeriodo(periodo));
    }

    // ==========================================
    // LISTAR POR EMPLEADO (historial — PANTALLA-08)
    // ==========================================

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<MovimientoPlanillaResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Historial de planillas del empleado",
                service.listarPorEmpleado(empleadoId));
    }

    // ==========================================
    // RESUMEN POR META PRESUPUESTAL (PANTALLA-05)
    // ==========================================

    @GetMapping("/resumen-por-meta/{periodo}")
    public ApiResponse<List<ResumenMetaDto>>
    resumenPorMeta(
            @PathVariable String periodo) {

        return new ApiResponse<>(
                "OK",
                "Resumen por meta",
                service.resumenPorMeta(periodo));
    }

    // ==========================================
    // ELIMINAR PLANILLA
    // ==========================================

    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return new ApiResponse<>(
                "OK",
                "Planilla eliminada",
                null);
    }

    // ==========================================
    // CAMBIAR ESTADO
    // ==========================================

    @PutMapping("/{id}/estado/{estado}")
    public ApiResponse<Void> cambiarEstado(
            @PathVariable Long id,
            @PathVariable String estado) {

        service.cambiarEstado(
                id,
                estado);

        return new ApiResponse<>(
                "OK",
                "Estado actualizado",
                null);
    }
}