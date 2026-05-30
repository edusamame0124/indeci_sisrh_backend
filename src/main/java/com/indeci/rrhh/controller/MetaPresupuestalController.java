package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
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
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/meta-presupuestal")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class MetaPresupuestalController {

    private final MetaPresupuestalService service;

    @GetMapping("/semaforo/{periodoId}")
    public ApiResponse<SemaforoPresupuestalDto> semaforo(@PathVariable Long periodoId) {
        return new ApiResponse<>("OK", "Semáforo presupuestal", service.semaforo(periodoId));
    }

    @PutMapping("/{periodoId}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> guardar(
            @PathVariable Long periodoId,
            @RequestBody List<MetaCertificacionDto> entradas) {
        service.guardar(periodoId, entradas);
        return new ApiResponse<>("OK", "Certificación presupuestal registrada", null);
    }
}
