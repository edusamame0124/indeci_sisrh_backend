package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.security.auth.SisrhSecurityExpressions;

import com.indeci.rrhh.service.BoletaPdfService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Spec 011 / B1 — M06 Boleta de pago PDF.
 * Base: {@code /api/rrhh/boleta}.
 */
@RestController
@RequestMapping("/api/rrhh/boleta")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class BoletaController {
    private final BoletaPdfService boletaPdfService;
    private final com.indeci.rrhh.service.BoletaDataService boletaDataService;

    /** Descarga la boleta de pago del empleado/período en PDF. */
    @GetMapping("/{empleadoId}/{periodo}/pdf")
    public ResponseEntity<byte[]> descargarPdf(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {

        byte[] pdf = boletaPdfService.generar(empleadoId, periodo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("boleta-" + empleadoId + "-" + periodo + ".pdf")
                .build());

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    /** FASE 4 - Descarga la boleta de pago del empleado/período en JSON para la UI Angular. */
    @GetMapping("/{empleadoId}/{periodo}/data")
    public ResponseEntity<com.indeci.rrhh.dto.BoletaPagoResponseDto> obtenerDataBoleta(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        
        com.indeci.rrhh.dto.BoletaPagoResponseDto dto = boletaDataService.obtenerBoletaData(empleadoId, periodo);
        return ResponseEntity.ok(dto);
    }
}
