package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.dto.SolicitudRrhhResponseDto;
import com.indeci.rrhh.dto.SolicitudWorkflowDocumentoDto;
import com.indeci.rrhh.service.SolicitudRrhhService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/solicitudes")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class SolicitudRrhhController {

    private final SolicitudRrhhService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(@RequestBody SolicitudRrhhDto dto) {
        service.registrar(dto);
        return new ApiResponse<>("OK", "Solicitud registrada", null);
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<SolicitudRrhhResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Solicitudes empleado", service.listarPorEmpleado(empleadoId));
    }

    @PutMapping("/enviar/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> enviar(
            @PathVariable Long id,
            @RequestBody SolicitudWorkflowDocumentoDto dto) {
        service.enviar(id, dto);
        return new ApiResponse<>("OK", "Solicitud enviada", null);
    }

    @PutMapping("/aprobar-jefe/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> aprobarJefe(
            @PathVariable Long id,
            @RequestBody SolicitudWorkflowDocumentoDto dto) {
        service.aprobarSupervisor(id, dto);
        return new ApiResponse<>("OK", "Solicitud aprobada por jefe", null);
    }

    @PutMapping("/rechazar-jefe/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> rechazarJefe(@PathVariable Long id) {
        service.rechazarSupervisor(id);
        return new ApiResponse<>("OK", "Solicitud rechazada por jefe", null);
    }

    @PutMapping("/aprobar-rrhh/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> aprobarRrhh(
            @PathVariable Long id,
            @RequestBody SolicitudWorkflowDocumentoDto dto) {
        service.aprobarRrhh(id, dto);
        return new ApiResponse<>("OK", "Solicitud aprobada por RRHH", null);
    }

    @PutMapping("/rechazar-rrhh/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> rechazarRrhh(@PathVariable Long id) {
        service.rechazarRrhh(id);
        return new ApiResponse<>("OK", "Solicitud rechazada por RRHH", null);
    }

    @PutMapping("/editar/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> editar(@PathVariable Long id, @RequestBody SolicitudRrhhDto dto) {
        service.editar(id, dto);
        return new ApiResponse<>("OK", "Solicitud editada", null);
    }

    @PutMapping("/anular/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> anular(@PathVariable Long id) {
        service.anular(id);
        return new ApiResponse<>("OK", "Solicitud anulada", null);
    }
}
