package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.MetaPptoLoteDetalleResponse;
import com.indeci.rrhh.dto.MetaPptoLoteDto;
import com.indeci.rrhh.dto.MetaPptoLoteResponse;
import com.indeci.rrhh.dto.MetaResolverDto;
import com.indeci.rrhh.service.MetaPptoLoteService;
import com.indeci.security.auth.SisrhSecurityExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rrhh/meta-ppto/lotes")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class MetaPptoLoteController {

    private final MetaPptoLoteService service;

    @GetMapping("/{anioDestino}")
    public ApiResponse<List<MetaPptoLoteResponse>> listar(@PathVariable Integer anioDestino) {
        return new ApiResponse<>("OK", "Lotes del año", service.listarPorAnio(anioDestino));
    }

    @GetMapping("/detalle/{loteId}")
    public ApiResponse<List<MetaPptoLoteDetalleResponse>> detalle(@PathVariable Long loteId) {
        return new ApiResponse<>("OK", "Detalle del lote", service.listarDetalle(loteId));
    }

    @GetMapping("/observados/{loteId}")
    public ApiResponse<List<MetaPptoLoteDetalleResponse>> observados(@PathVariable Long loteId) {
        return new ApiResponse<>("OK", "Excepciones del lote", service.listarObservados(loteId));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<MetaPptoLoteResponse> crear(
            @RequestBody MetaPptoLoteDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Lote creado", service.crearLote(dto, user.getUsername()));
    }

    @PostMapping("/{loteId}/procesar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<MetaPptoLoteResponse> procesar(
            @PathVariable Long loteId,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Proceso masivo completado", service.procesarCopiaAnioAnterior(loteId, user.getUsername()));
    }

    @PostMapping("/{loteId}/resolver")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<MetaPptoLoteDetalleResponse> resolver(
            @PathVariable Long loteId,
            @RequestBody MetaResolverDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Excepción resuelta", service.resolverExcepcion(loteId, dto, user.getUsername()));
    }

    @PostMapping("/{loteId}/publicar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<MetaPptoLoteResponse> publicar(
            @PathVariable Long loteId,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Lote publicado", service.publicar(loteId, user.getUsername()));
    }

    @DeleteMapping("/{loteId}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> anular(
            @PathVariable Long loteId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        service.anular(loteId, body.getOrDefault("motivo", "Anulado por usuario"), user.getUsername());
        return new ApiResponse<>("OK", "Lote anulado", null);
    }
}
