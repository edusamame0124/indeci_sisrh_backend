package com.indeci.rrhh.controller;

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

    private final SolicitudRrhhDocService
            service;

    @PostMapping
    public ApiResponse<Void>
    registrar(
            @RequestBody
            SolicitudRrhhDocDto dto) {

        service.registrar(dto);

        return new ApiResponse<>(
                "OK",
                "Documento registrado",
                null);
    }

    @GetMapping("/{solicitudId}")
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