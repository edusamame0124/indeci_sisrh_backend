package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ElegibilidadVinculoDto;
import com.indeci.rrhh.dto.EmpleadoRemuneracionHistDto;
import com.indeci.rrhh.dto.RemuneracionCambioInput;
import com.indeci.rrhh.service.ElegibilidadVinculoService;
import com.indeci.rrhh.service.RemuneracionHistService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** F2 — Historial remunerativo del vínculo (INDECI_EMPLEADO_REMUNERACION_HIST). */
@RestController
@RequestMapping("/api/rrhh/empleado-planilla")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class RemuneracionHistController {

    private final RemuneracionHistService service;
    private final ElegibilidadVinculoService elegibilidadService;

    /** F4a — elegibilidad calculada del vínculo para planilla / MCPP. */
    @GetMapping("/{empleadoPlanillaId}/elegibilidad")
    public ApiResponse<ElegibilidadVinculoDto> elegibilidad(
            @PathVariable Long empleadoPlanillaId) {
        return new ApiResponse<>("OK", "Elegibilidad del vínculo",
                elegibilidadService.evaluar(empleadoPlanillaId));
    }

    @GetMapping("/{empleadoPlanillaId}/remuneracion-hist")
    public ApiResponse<List<EmpleadoRemuneracionHistDto>> listar(
            @PathVariable Long empleadoPlanillaId) {
        return new ApiResponse<>("OK", "Historial remunerativo",
                service.listar(empleadoPlanillaId));
    }

    @PostMapping("/{empleadoPlanillaId}/remuneracion-hist")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<EmpleadoRemuneracionHistDto> registrar(
            @PathVariable Long empleadoPlanillaId,
            @RequestBody @Valid RemuneracionCambioInput input) {
        return new ApiResponse<>("OK", "Cambio remunerativo registrado",
                service.registrarCambio(empleadoPlanillaId, input));
    }

    @DeleteMapping("/{empleadoPlanillaId}/remuneracion-hist/{historialId}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> eliminar(
            @PathVariable Long empleadoPlanillaId,
            @PathVariable Long historialId) {
        service.eliminar(empleadoPlanillaId, historialId);
        return new ApiResponse<>("OK", "Cambio remunerativo eliminado", null);
    }
}
