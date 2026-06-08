package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaImportConfirmRequest;
import com.indeci.rrhh.dto.AsistenciaImportHistorialDto;
import com.indeci.rrhh.dto.AsistenciaImportPreviewDto;
import com.indeci.rrhh.dto.AsistenciaValidacionBatchDto;
import com.indeci.rrhh.service.AsistenciaImportService;
import com.indeci.security.auth.SisrhSecurityExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rrhh/asistencia/import")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
public class AsistenciaImportController {

    private final AsistenciaImportService importService;

    @PostMapping("/preview")
    public ApiResponse<AsistenciaImportPreviewDto> preview(
            @RequestParam String periodo,
            @RequestParam("archivo") MultipartFile archivo) {
        return new ApiResponse<>("OK",
                "Vista previa de importación generada",
                importService.preview(periodo, archivo));
    }

    @PostMapping("/confirm")
    public ApiResponse<AsistenciaImportPreviewDto> confirmarPorBody(
            @RequestBody AsistenciaImportConfirmRequest request) {
        if (request == null || request.getImportacionId() == null) {
            throw new NegocioException("El identificador de importación es obligatorio.");
        }
        return new ApiResponse<>("OK",
                "Importación confirmada",
                importService.confirmar(request.getImportacionId(), request));
    }

    @PostMapping("/{importacionId}/confirm")
    public ApiResponse<AsistenciaImportPreviewDto> confirmar(
            @PathVariable Long importacionId,
            @RequestBody(required = false) AsistenciaImportConfirmRequest request) {
        return new ApiResponse<>("OK",
                "Importación confirmada",
                importService.confirmar(importacionId, request));
    }

    @GetMapping
    public ApiResponse<Page<AsistenciaImportHistorialDto>> historial(
            @RequestParam(required = false) String periodo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return new ApiResponse<>("OK",
                "Historial de importaciones",
                importService.historial(periodo, PageRequest.of(Math.max(page, 0), safeSize)));
    }

    @GetMapping("/{importacionId}")
    public ApiResponse<AsistenciaImportPreviewDto> detalle(@PathVariable Long importacionId) {
        return new ApiResponse<>("OK",
                "Detalle de importación",
                importService.detalle(importacionId));
    }

    @GetMapping(value = "/{importacionId}/errores.csv", produces = "text/csv")
    public ResponseEntity<byte[]> erroresCsv(@PathVariable Long importacionId) {
        byte[] csv = importService.erroresCsv(importacionId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("asistencia-importacion-" + importacionId + "-errores.csv")
                .build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @PostMapping("/{importacionId}/validar-cabeceras")
    public ApiResponse<AsistenciaValidacionBatchDto> validarCabeceras(
            @PathVariable Long importacionId) {
        return new ApiResponse<>("OK",
                "Cabeceras de asistencia validadas",
                importService.validarCabeceras(importacionId));
    }
}
