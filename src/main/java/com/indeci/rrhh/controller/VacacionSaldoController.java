package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.VacacionSaldoResponseDto;
import com.indeci.rrhh.service.VacacionSaldoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * Solo lectura — consumido por el Portal del Empleado (self-service). El registro/corrección
 * del saldo se hace desde el Padrón Vacacional ("Provisionar Auto", ver
 * {@code VacacionController#provisionarAuto}); el mantenimiento manual fue retirado.
 */
@RestController
@RequestMapping("/api/rrhh/vacacion-saldo")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class VacacionSaldoController {

    private final VacacionSaldoService service;

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<VacacionSaldoResponseDto>> listarPorEmpleado(
            @PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Saldo de vacaciones del empleado",
                service.listarPorEmpleado(empleadoId));
    }
}
