package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.security.auth.SisrhSecurityExpressions;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.SolicitudRrhhDocDto;
import com.indeci.rrhh.dto.SolicitudRrhhDocResponseDto;
import com.indeci.rrhh.service.SolicitudRrhhDocService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/solicitudes-doc")
@RequiredArgsConstructor
public class SolicitudRrhhDocController {

    private final SolicitudRrhhDocService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> registrar(
            @RequestBody
            SolicitudRrhhDocDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Documento registrado",
                null);
    }

    /**
     * Documentos adjuntos de una papeleta (trazabilidad + descarga en los diálogos de
     * aprobación). Antes exigía solo EMP_READ (permiso administrativo de legajo) — que ni el
     * rol Empleado (dueño de su propia papeleta) ni el rol dedicado a aprobar papeletas por
     * RRHH (PAP_APROBAR_RRHH/PAP_RRHH, sin EMP_READ) tienen, bloqueando con 403 la trazabilidad
     * del propio empleado y la descarga de sustento del evaluador RRHH. Acepta también los
     * permisos de papeleta (PAP_EMPLEADO/PAP_JEFE/PAP_RRHH/PAP_APROBAR_RRHH). El guard de
     * propiedad para PAP_EMPLEADO (evita leer documentos de una papeleta ajena) vive en el
     * servicio — ver {@link SolicitudRrhhDocService#listar}.
     */
    @GetMapping("/{solicitudId}")
    @PreAuthorize("hasAuthority('PAP_EMPLEADO') or hasAuthority('PAP_JEFE') "
            + "or hasAuthority('PAP_RRHH') or hasAuthority('PAP_APROBAR_RRHH') "
            + "or " + SisrhSecurityExpressions.EMP_READ)
    public ApiResponse<
            List<SolicitudRrhhDocResponseDto>>
    listar(
            @PathVariable Long solicitudId) {

        return new ApiResponse<>(
                "OK",
                "Documentos solicitud",
                service.listar(
                        solicitudId));
    }
}