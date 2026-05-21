package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.MetaCertificacionDto;
import com.indeci.rrhh.dto.SemaforoPresupuestalDto;
import com.indeci.rrhh.service.MetaPresupuestalService;

import lombok.RequiredArgsConstructor;

/**
 * Spec 012 / C1 · P-05 — Semáforo presupuestal por meta.
 *
 * <p>{@code GET /semaforo/{periodoId}} devuelve certificado vs comprometido por
 * meta; {@code PUT /{periodoId}} registra los montos certificados que carga
 * Tesorería. Es información de control: no toca el flujo de aprobación (B7).
 */
@RestController
@RequestMapping("/api/rrhh/meta-presupuestal")
@RequiredArgsConstructor
public class MetaPresupuestalController {

    private final MetaPresupuestalService service;

    @GetMapping("/semaforo/{periodoId}")
    public ApiResponse<SemaforoPresupuestalDto> semaforo(@PathVariable Long periodoId) {
        return new ApiResponse<>("OK", "Semáforo presupuestal", service.semaforo(periodoId));
    }

    @PutMapping("/{periodoId}")
    public ApiResponse<Void> guardar(
            @PathVariable Long periodoId,
            @RequestBody List<MetaCertificacionDto> entradas) {
        service.guardar(periodoId, entradas);
        return new ApiResponse<>("OK", "Certificación presupuestal registrada", null);
    }
}
