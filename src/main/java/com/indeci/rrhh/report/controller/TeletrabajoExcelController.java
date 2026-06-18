package com.indeci.rrhh.report.controller;

import com.indeci.rrhh.report.service.TeletrabajoExcelService;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rrhh/teletrabajo")
@RequiredArgsConstructor
public class TeletrabajoExcelController {

    private final TeletrabajoExcelService service;

    @GetMapping("/{id}/excel")
    public ResponseEntity<Resource>
    descargar(
            @PathVariable Long id) {

        String ruta =
                service.generarExcel(
                        id);

        Resource resource =
                new FileSystemResource(
                        ruta);

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=teletrabajo.xlsx")
                .body(resource);
    }
}