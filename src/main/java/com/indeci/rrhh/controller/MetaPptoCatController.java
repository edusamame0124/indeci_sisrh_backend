package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.CambioEstadoMasivoDto;
import com.indeci.rrhh.dto.CambioEstadoMasivoResultDto;
import com.indeci.rrhh.dto.MetaPptoCatDto;
import com.indeci.rrhh.dto.MetaPptoCatImportDto;
import com.indeci.rrhh.dto.MetaPptoCatResponse;
import com.indeci.rrhh.service.MetaPptoCatService;
import com.indeci.security.auth.SisrhSecurityExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rrhh/meta-ppto/catalogo")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class MetaPptoCatController {

    private final MetaPptoCatService service;

    @GetMapping("/{anioFiscal}")
    public ApiResponse<List<MetaPptoCatResponse>> listar(@PathVariable Integer anioFiscal) {
        return new ApiResponse<>("OK", "Catálogo de metas", service.listarPorAnio(anioFiscal));
    }

    @GetMapping("/detalle/{id}")
    public ApiResponse<MetaPptoCatResponse> obtener(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Meta presupuestal", service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<MetaPptoCatResponse> crear(
            @RequestBody MetaPptoCatDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Meta creada", service.crear(dto, user.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<MetaPptoCatResponse> editar(
            @PathVariable Long id,
            @RequestBody MetaPptoCatDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Meta actualizada", service.editar(id, dto, user.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> anular(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        service.anular(id, body.getOrDefault("motivo", "Anulado por usuario"), user.getUsername());
        return new ApiResponse<>("OK", "Meta anulada", null);
    }

    @PostMapping("/importar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<List<MetaPptoCatResponse>> importar(
            @RequestBody MetaPptoCatImportDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Importación completada", service.importar(dto, user.getUsername()));
    }

    @PostMapping("/publicar/{anioFiscal}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> publicar(
            @PathVariable Integer anioFiscal,
            @AuthenticationPrincipal UserDetails user) {
        service.publicarCatalogo(anioFiscal, user.getUsername());
        return new ApiResponse<>("OK", "Catálogo publicado para " + anioFiscal, null);
    }

    /** B3 — Cambio masivo de estado para metas seleccionadas. */
    @PatchMapping("/estado")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<CambioEstadoMasivoResultDto> cambiarEstado(
            @RequestBody CambioEstadoMasivoDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Estados actualizados",
                service.cambiarEstadoMasivo(dto, user.getUsername()));
    }
}
