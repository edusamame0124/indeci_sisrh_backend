package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;

import com.indeci.rrhh.dto.*;

import com.indeci.rrhh.service
        .SolicitudRrhhService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/solicitudes")
@RequiredArgsConstructor
public class SolicitudRrhhController {

    private final SolicitudRrhhService service;

    // ==========================================
    // REGISTRAR
    // ==========================================

    @PostMapping
    public ApiResponse<Void> registrar(
            @RequestBody SolicitudRrhhDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Solicitud registrada",
                null);
    }

    // ==========================================
    // LISTAR EMPLEADO
    // ==========================================

    @GetMapping("/{empleadoId}")
    public ApiResponse<
            List<SolicitudRrhhResponseDto>>
    listar(@PathVariable Long empleadoId) {

        return new ApiResponse<>(
                "OK",
                "Solicitudes empleado",
                service.listarPorEmpleado(
                        empleadoId));
    }
    
    @PutMapping("/enviar/{id}")
    public ApiResponse<Void>
    enviar(
            @PathVariable Long id,

            @RequestBody
            SolicitudWorkflowDocumentoDto dto) {

        service.enviar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Solicitud enviada",
                null);
    }
    
    @PutMapping("/aprobar-jefe/{id}")
    public ApiResponse<Void>
    aprobarJefe(
            @PathVariable Long id,

            @RequestBody
            SolicitudWorkflowDocumentoDto dto) {

        service.aprobarSupervisor(
                id,
                dto);

        return new ApiResponse<>(
                "OK",
                "Solicitud aprobada por jefe",
                null);
    }
    
    @PutMapping("/rechazar-jefe/{id}")
    public ApiResponse<Void>
    rechazarJefe(@PathVariable Long id) {

        service.rechazarSupervisor(id);

        return new ApiResponse<>(
                "OK",
                "Solicitud rechazada por jefe",
                null);
    }
    
    @PutMapping("/aprobar-rrhh/{id}")
    public ApiResponse<Void>
    aprobarRrhh(
            @PathVariable Long id,

            @RequestBody
            SolicitudWorkflowDocumentoDto dto) {

        service.aprobarRrhh(
                id,
                dto);

        return new ApiResponse<>(
                "OK",
                "Solicitud aprobada por RRHH",
                null);
    }
    @PutMapping("/rechazar-rrhh/{id}")
    public ApiResponse<Void>
    rechazarRrhh(@PathVariable Long id) {

        service.rechazarRrhh(id);

        return new ApiResponse<>(
                "OK",
                "Solicitud rechazada por RRHH",
                null);
    }
    
    @PutMapping("/editar/{id}")
    public ApiResponse<Void>
    editar(@PathVariable Long id,
           @RequestBody SolicitudRrhhDto dto) {

        service.editar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Solicitud editada",
                null);
    }
    
    @PutMapping("/anular/{id}")
    public ApiResponse<Void>
    anular(@PathVariable Long id) {

        service.anular(id);

        return new ApiResponse<>(
                "OK",
                "Solicitud anulada",
                null);
    }
}