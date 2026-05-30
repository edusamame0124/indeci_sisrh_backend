package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.security.auth.SisrhSecurityExpressions;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.rrhh.service.ArchivoBancoService;

import lombok.RequiredArgsConstructor;

/**
 * Spec 013 / C1 · P-07 — Descarga del archivo bancario por banco.
 * Base: {@code /api/rrhh/archivo-banco}.
 */
@RestController
@RequestMapping("/api/rrhh/archivo-banco")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.RPT_READ)
public class ArchivoBancoController {

    private final ArchivoBancoService archivoBancoService;

    /** Descarga un ZIP con un .txt de abonos por banco para el período. */
    @GetMapping("/{periodo}/zip")
    public ResponseEntity<byte[]> descargarZip(@PathVariable String periodo) {

        byte[] zip = archivoBancoService.generarZip(periodo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("archivo-bancos-" + periodo.replace("-", "") + ".zip")
                .build());

        return ResponseEntity.ok().headers(headers).body(zip);
    }
}
