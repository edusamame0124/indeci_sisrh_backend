package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.DeteccionEquivRequestDto;
import com.indeci.rrhh.dto.DeteccionEquivResultDto;
import com.indeci.rrhh.dto.MetaPptoEquivDto;
import com.indeci.rrhh.dto.MetaPptoEquivResponse;
import com.indeci.rrhh.service.MetaPptoEquivService;
import com.indeci.security.auth.SisrhSecurityExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rrhh/meta-ppto/equivalencias")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class MetaPptoEquivController {

    private final MetaPptoEquivService service;

    @GetMapping
    public ApiResponse<List<MetaPptoEquivResponse>> listar(
            @RequestParam Integer anioOrigen,
            @RequestParam Integer anioDestino) {
        return new ApiResponse<>("OK", "Equivalencias", service.listarPorAnios(anioOrigen, anioDestino));
    }

    @GetMapping("/activas")
    public ApiResponse<List<MetaPptoEquivResponse>> listarActivas(
            @RequestParam Integer anioOrigen,
            @RequestParam Integer anioDestino) {
        return new ApiResponse<>("OK", "Equivalencias activas", service.listarActivasPorAnios(anioOrigen, anioDestino));
    }

    @GetMapping("/{id}")
    public ApiResponse<MetaPptoEquivResponse> obtener(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Equivalencia", service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<MetaPptoEquivResponse> crear(
            @RequestBody MetaPptoEquivDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Equivalencia creada", service.crear(dto, user.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<MetaPptoEquivResponse> editar(
            @PathVariable Long id,
            @RequestBody MetaPptoEquivDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Equivalencia actualizada", service.editar(id, dto, user.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> anular(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        service.anular(id, body.getOrDefault("motivo", "Anulado por usuario"), user.getUsername());
        return new ApiResponse<>("OK", "Equivalencia anulada", null);
    }

    /** B2 — Detección automática de equivalencias por coincidencia estructural. */
    @PostMapping("/detectar-auto")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<List<DeteccionEquivResultDto>> detectarAuto(
            @RequestBody DeteccionEquivRequestDto req,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Detección completada",
                service.detectarEquivalenciasAuto(req, user.getUsername()));
    }
}
