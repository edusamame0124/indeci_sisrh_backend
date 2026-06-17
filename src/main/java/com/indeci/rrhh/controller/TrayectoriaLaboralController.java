package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.TrayectoriaLaboralResponseDto;
import com.indeci.rrhh.service.TrayectoriaLaboralService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/trayectoria-laboral")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class TrayectoriaLaboralController {

    private final TrayectoriaLaboralService service;

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<
            List<TrayectoriaLaboralResponseDto>>
    listarPorEmpleado(
            @PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Trayectoria laboral",
                service.listarPorEmpleado(
                        empleadoId));
    }
}