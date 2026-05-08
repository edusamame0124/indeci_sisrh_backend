package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.PeriodoPlanillaDto;
import com.indeci.rrhh.dto.PeriodoPlanillaResponseDto;
import com.indeci.rrhh.service.PeriodoPlanillaService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/periodo-planilla")
@RequiredArgsConstructor
public class PeriodoPlanillaController {

    private final PeriodoPlanillaService service;

    @PostMapping
    public ApiResponse<Void> guardar(
            @RequestBody PeriodoPlanillaDto dto) {

        service.guardar(dto);

        return new ApiResponse<>(
                "OK",
                "Periodo registrado",
                null);
    }

    @GetMapping
    public ApiResponse<List<PeriodoPlanillaResponseDto>>
    listar() {

        return new ApiResponse<>(
                "OK",
                "Lista periodos",
                service.listar());
    }

    @PutMapping("/cerrar/{id}")
    public ApiResponse<Void> cerrar(
            @PathVariable Long id) {

        service.cerrar(id);

        return new ApiResponse<>(
                "OK",
                "Periodo cerrado",
                null);
    }

    @PutMapping("/reabrir/{id}")
    public ApiResponse<Void> reabrir(
            @PathVariable Long id) {

        service.reabrir(id);

        return new ApiResponse<>(
                "OK",
                "Periodo reabierto",
                null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return new ApiResponse<>(
                "OK",
                "Periodo eliminado",
                null);
    }
}