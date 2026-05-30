package com.indeci.rrhh.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.dto.SolicitudRrhhResponseDto;
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

    // ==========================================
    // LISTAR EMPLEADO
    // ==========================================

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<SolicitudRrhhResponseDto>> listarEmpleado(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Solicitudes empleado", service.listarPorEmpleado(empleadoId));
    }

    @PutMapping("/enviar/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> enviar(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "observacion", required = false) String observacion) {

        service.enviar(id, file, observacion);
        return new ApiResponse<>("OK", "Solicitud enviada", null);
    }

    @PutMapping("/aprobar-jefe/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> aprobarJefe(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "observacion", required = false) String observacion) {

        service.aprobarSupervisor(id, file, observacion);
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
    public ApiResponse<Void> aprobarRrhh(@PathVariable Long id, @RequestBody Object dto) {
        // Nota: Mantenemos la firma original de tu compañero
        service.aprobarRrhh(id, null); 
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
    
    @GetMapping("/jefe/{jefeId}")
    public ApiResponse<List<SolicitudRrhhResponseDto>> listarJefe(@PathVariable Long jefeId) {
        return new ApiResponse<>("OK", "Listado correcto", service.listarPorJefe(jefeId));
    }
    
    @GetMapping("/todas")
    public ApiResponse<List<SolicitudRrhhResponseDto>> listarTodas() {
        return new ApiResponse<>("OK", "Listado correcto", service.listarTodas());
    }
}