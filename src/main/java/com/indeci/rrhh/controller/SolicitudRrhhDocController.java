package com.indeci.rrhh.controller;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
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

    /**
     * Remediación: agrega el PDF real como nueva versión del documento de la etapa JEFE/RRHH,
     * sin reabrir ni cambiar el estado de la solicitud. Restringido a PAP_APROBAR_RRHH: el mismo
     * rol que da la aprobación final, ya que reescribe el expediente documental de aprobaciones.
     */
    @PutMapping(value = "/subsanar/{solicitudId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAP_APROBAR_RRHH')")
    public ApiResponse<Void> subsanar(
            @PathVariable Long solicitudId,
            @RequestParam("etapa") String etapa,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "observacion", required = false) String observacion) {

        service.subsanar(solicitudId, etapa, file, observacion);

        return new ApiResponse<>(
                "OK",
                "Documento subsanado",
                null);
    }
}