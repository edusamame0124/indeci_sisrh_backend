package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.VacacionSaldoDto;
import com.indeci.rrhh.dto.VacacionSaldoResponseDto;
import com.indeci.rrhh.service.VacacionSaldoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import com.indeci.rrhh.service.VacacionProvisionService;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/rrhh/vacacion-saldo")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class VacacionSaldoController {

    private final VacacionSaldoService service;
    private final VacacionProvisionService provisionService;

    @PostMapping("/provisionar/{empleadoId}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> provisionar(
            @PathVariable Long empleadoId,
            @RequestParam(required = false) Integer anioPeriodo) {
        int anio = (anioPeriodo != null) ? anioPeriodo : java.time.LocalDate.now().getYear();
        provisionService.provisionar(empleadoId, anio);
        return new ApiResponse<>("OK", "Provisión generada correctamente", null);
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
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
