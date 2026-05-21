package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.VacacionSaldoDto;
import com.indeci.rrhh.dto.VacacionSaldoResponseDto;
import com.indeci.rrhh.service.VacacionSaldoService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Saldo de vacaciones del empleado (SPEC §12.2 PANTALLA-08).
 * Base: {@code /api/rrhh/vacacion-saldo}.
 */
@RestController
@RequestMapping("/api/rrhh/vacacion-saldo")
@RequiredArgsConstructor
public class VacacionSaldoController {

    private final VacacionSaldoService service;

    @PostMapping
    public ApiResponse<Void> guardar(@RequestBody VacacionSaldoDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Saldo de vacaciones guardado", null);
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<VacacionSaldoResponseDto>> listarPorEmpleado(
            @PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Saldo de vacaciones del empleado",
                service.listarPorEmpleado(empleadoId));
    }
}
