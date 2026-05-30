package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.security.auth.SisrhSecurityExpressions;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.SolicitudRrhhHistResponseDto;
import com.indeci.rrhh.service.SolicitudRrhhHistService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/solicitudes-hist")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class SolicitudRrhhHistController {

    private final SolicitudRrhhHistService
            service;

    @GetMapping("/{solicitudId}")
    public ApiResponse<
            List<SolicitudRrhhHistResponseDto>>
    listar(
            @PathVariable Long solicitudId) {

        return new ApiResponse<>(
                "OK",
                "Historial solicitud",
                service.listar(
                        solicitudId));
    }
}