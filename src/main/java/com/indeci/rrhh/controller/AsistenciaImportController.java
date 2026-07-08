package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaAceptarObservadasRequest;
import com.indeci.rrhh.dto.AsistenciaAnularRequest;
import com.indeci.rrhh.dto.AsistenciaImportConfirmRequest;
import com.indeci.rrhh.dto.AsistenciaImportFilaDetalleDto;
import com.indeci.rrhh.dto.AsistenciaImportHistorialDto;
import com.indeci.rrhh.dto.AsistenciaImportPreviewDto;
import com.indeci.rrhh.dto.AsistenciaImportResumenDto;
import com.indeci.rrhh.dto.AsistenciaValidacionBatchDto;
import com.indeci.rrhh.dto.MarcadorAliasDto;
import com.indeci.rrhh.dto.MarcadorAliasRequest;
import com.indeci.rrhh.dto.MarcadorSinMapeoDto;
import com.indeci.rrhh.service.AsistenciaImportService;
import com.indeci.rrhh.service.MarcadorAliasService;
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

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/asistencia/import")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
public class AsistenciaImportController {

    private final AsistenciaImportService importService;
    private final MarcadorAliasService marcadorAliasService;

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

    @GetMapping("/{importacionId}/detalles")
    public ApiResponse<Page<AsistenciaImportFilaDetalleDto>> detalles(
            @PathVariable Long importacionId,
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "false") boolean soloErrores,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        return new ApiResponse<>("OK",
                "Detalle paginado de importación",
                importService.detalles(importacionId, dni, nombre, estado, soloErrores,
                        PageRequest.of(Math.max(page, 0), safeSize)));
    }

    @GetMapping("/{importacionId}/resumen")
    public ApiResponse<AsistenciaImportResumenDto> resumen(@PathVariable Long importacionId) {
        return new ApiResponse<>("OK",
                "Resumen de importación",
                importService.resumen(importacionId));
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

    @GetMapping(value = "/{importacionId}/errores.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> erroresXlsx(@PathVariable Long importacionId) {
        byte[] xlsx = importService.erroresXlsx(importacionId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("asistencia-importacion-" + importacionId + "-errores.xlsx")
                .build());
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }

    @PostMapping("/{importacionId}/validar-cabeceras")
    public ApiResponse<AsistenciaValidacionBatchDto> validarCabeceras(
            @PathVariable Long importacionId) {
        return new ApiResponse<>("OK",
                "Cabeceras de asistencia validadas",
                importService.validarCabeceras(importacionId));
    }

    @PostMapping("/{importacionId}/aceptar-observadas")
    public ApiResponse<Integer> aceptarObservadas(
            @PathVariable Long importacionId,
            @RequestBody AsistenciaAceptarObservadasRequest request) {
        int aceptadas = importService.aceptarObservadas(
                importacionId,
                request != null ? request.getIdsFilas() : null,
                request != null ? request.getMotivo() : null);
        return new ApiResponse<>("OK", "Filas observadas aceptadas", aceptadas);
    }

    @PostMapping("/{importacionId}/anular")
    public ApiResponse<AsistenciaImportPreviewDto> anular(
            @PathVariable Long importacionId,
            @RequestBody AsistenciaAnularRequest request) {
        return new ApiResponse<>("OK",
                "Importación anulada",
                importService.anular(importacionId, request != null ? request.getMotivo() : null));
    }

    /** F2 (COEN) — nombres del marcador sin mapear a un empleado (SPEC D1). */
    @GetMapping("/{importacionId}/sin-mapeo")
    public ApiResponse<List<MarcadorSinMapeoDto>> sinMapeo(@PathVariable Long importacionId) {
        return new ApiResponse<>("OK",
                "Nombres sin mapear",
                marcadorAliasService.listarSinMapeo(importacionId));
    }

    /** F2 (COEN) — mapea un nombre del marcador a un empleado (crea/actualiza el alias). */
    @PostMapping("/marcador-alias")
    public ApiResponse<MarcadorAliasDto> mapearAlias(@RequestBody MarcadorAliasRequest request) {
        return new ApiResponse<>("OK",
                "Nombre mapeado al empleado",
                marcadorAliasService.mapear(request));
    }
}
