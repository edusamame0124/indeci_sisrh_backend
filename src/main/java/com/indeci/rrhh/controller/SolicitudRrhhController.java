package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;

import com.indeci.rrhh.dto.*;

import com.indeci.rrhh.service
        .SolicitudRrhhService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/rrhh/solicitudes")
@RequiredArgsConstructor
public class SolicitudRrhhController {

    private final SolicitudRrhhService service;

    // ==========================================
    // REGISTRAR
    // ==========================================

    @PreAuthorize("hasAuthority('PAP_EMPLEADO')")
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

    @PreAuthorize("hasAuthority('PAP_EMPLEADO')")
    @GetMapping("/mis-solicitudes")
    public ApiResponse<List<SolicitudRrhhResponseDto>>
    misSolicitudes() {

        return new ApiResponse<>(
                "OK",
                "Solicitudes empleado",
                service.listarMisSolicitudes());
    }
    
    @PutMapping("/enviar/{id}")
    public ApiResponse<Void>
    enviar(

            @PathVariable Long id,

            @RequestParam("file")
            MultipartFile file,

            @RequestParam(
                    value = "observacion",
                    required = false)
            String observacion) {

    	service.enviar(
    	        id,
    	        file,
    	        observacion);

        return new ApiResponse<>(
                "OK",
                "Solicitud enviada",
                null);
    }
    
    @PreAuthorize("hasAuthority('PAP_JEFE')")
    @PutMapping("/aprobar-jefe/{id}")
    public ApiResponse<Void>
    aprobarJefe(

            @PathVariable Long id,

            @RequestParam("file")
            MultipartFile file,

            @RequestParam(
                    value = "observacion",
                    required = false)
            String observacion) {

        service.aprobarSupervisor(
                id,
                file,
                observacion);

        return new ApiResponse<>(
                "OK",
                "Solicitud aprobada por jefe",
                null);
    }
    
    @PreAuthorize("hasAuthority('PAP_JEFE')")
    @PutMapping("/rechazar-jefe/{id}")
    public ApiResponse<Void>
    rechazarJefe(@PathVariable Long id) {

        service.rechazarSupervisor(id);

        return new ApiResponse<>(
                "OK",
                "Solicitud rechazada por jefe",
                null);
    }
    
    @PreAuthorize("hasAuthority('PAP_RRHH')")
    @PutMapping("/aprobar-rrhh/{id}")
    public ApiResponse<Void>
    aprobarRrhh(

            @PathVariable Long id,

            @RequestParam("file")
            MultipartFile file,

            @RequestParam(
                    value = "observacion",
                    required = false)
            String observacion) {

        service.aprobarRrhh(
                id,
                file,
                observacion);

        return new ApiResponse<>(
                "OK",
                "Solicitud aprobada por RRHH",
                null);
    }
    
    @PreAuthorize("hasAuthority('PAP_RRHH')")
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
    
    @PreAuthorize("hasAuthority('PAP_JEFE')")
    @GetMapping("/mis-colaboradores")
    public ApiResponse<List<SolicitudRrhhResponseDto>>
    misColaboradores() {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                service.listarMisColaboradores());
    }
    
    @PreAuthorize("hasAuthority('PAP_RRHH')")
    @GetMapping("/todas")
    public ApiResponse<List<SolicitudRrhhResponseDto>>
    listarTodas() {

        return new ApiResponse<>(
                "OK",
                "Listado correcto",
                service.listarTodas());
    }
}