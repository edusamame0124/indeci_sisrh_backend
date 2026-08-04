package com.indeci.rrhh.report.controller;


import com.indeci.rrhh.report.service.PapeletaReportService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rrhh/reportes")
@RequiredArgsConstructor
//@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class PapeletaReportController {

    private final PapeletaReportService
            service;

    @PostMapping("/papeleta/{solicitudId}")
    public ResponseEntity<Resource>
    generar(
            @PathVariable Long solicitudId) {

        byte[] pdf =
                service.generarPdf(
                        solicitudId);

        Resource resource =
                new ByteArrayResource(
                        pdf);

        return ResponseEntity.ok()
                .contentType(
                        MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=papeleta.pdf")
                .body(resource);
    }
}