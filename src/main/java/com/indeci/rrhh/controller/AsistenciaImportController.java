package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaAceptarObservadasRequest;
import com.indeci.rrhh.dto.AsistenciaAnularRequest;
import com.indeci.rrhh.dto.AsistenciaImportConfirmRequest;
import com.indeci.rrhh.dto.AsistenciaImportFilaDetalleDto;
import com.indeci.rrhh.dto.AsistenciaImportHistorialDto;
import com.indeci.rrhh.dto.AsistenciaImportJobDto;
import com.indeci.rrhh.dto.AsistenciaImportPreviewDto;
import com.indeci.rrhh.dto.AsistenciaImportResumenDto;
import com.indeci.rrhh.dto.AsistenciaValidacionBatchDto;
import com.indeci.rrhh.dto.MarcadorAliasDto;
import com.indeci.rrhh.dto.MarcadorAliasRequest;
import com.indeci.rrhh.dto.MarcadorSinMapeoDto;
import com.indeci.rrhh.service.AsistenciaImportService;
import com.indeci.rrhh.service.MarcadorAliasService;
import com.indeci.rrhh.service.asistencia.AsistenciaImportAsyncRunner;
import com.indeci.rrhh.service.asistencia.AsistenciaImportJob;
import com.indeci.rrhh.service.asistencia.AsistenciaImportJobRegistry;
import com.indeci.security.auth.SisrhSecurityExpressions;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rrhh/asistencia/import")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
public class AsistenciaImportController {

    private final AsistenciaImportService importService;
    private final MarcadorAliasService marcadorAliasService;
    private final AsistenciaImportJobRegistry jobRegistry;
    private final AsistenciaImportAsyncRunner asyncRunner;

    /**
     * Validación SÍNCRONA — <b>fallback / legacy</b>. Procesa el CSV en una sola llamada bloqueante,
     * por lo que solo se recomienda para <b>cargas ligeras</b>: integraciones M2M, consumo por
     * cURL/Postman de otras áreas, o archivos pequeños donde el overhead del polling no se justifica.
     *
     * <p><b>El cliente principal (UI) debe usar {@link #previewAsync} ({@code POST /preview/async})</b>:
     * con archivos grandes (miles de filas), la vía síncrona puede agotar el timeout del gateway (504).
     * No se marca {@code @Deprecated} porque es un fallback soportado, no un endpoint obsoleto.
     */
    @Operation(
            summary = "Validación síncrona (fallback para cargas ligeras / M2M)",
            description = "Valida el CSV en una sola llamada bloqueante. Para archivos grandes o desde "
                    + "la UI, usar POST /preview/async (job asíncrono con progreso por polling). Se "
                    + "conserva para integraciones M2M y cargas pequeñas; NO es obsoleto.")
    @PostMapping("/preview")
    public ApiResponse<AsistenciaImportPreviewDto> preview(
            @RequestParam String periodo,
            @RequestParam("archivo") MultipartFile archivo) {
        return new ApiResponse<>("OK",
                "Vista previa de importación generada",
                importService.preview(periodo, archivo));
    }

    /**
     * Opción B (recomendada) — Inicia la validación en un job ASÍNCRONO y responde el {@code jobId}
     * de inmediato (evita timeouts 504 con archivos grandes). El frontend consulta el progreso por
     * polling en {@code GET /import/job/{jobId}}. Es el <b>camino principal de la UI</b>.
     */
    @Operation(
            summary = "Validación asíncrona (recomendada) — inicia un job y devuelve jobId",
            description = "Camino principal de la UI. Responde al instante con { jobId }; el progreso "
                    + "se consulta por polling en GET /import/job/{jobId}. Evita timeouts (504) con "
                    + "archivos grandes.")
    @PostMapping("/preview/async")
    public ApiResponse<Map<String, String>> previewAsync(
            @RequestParam String periodo,
            @RequestParam("archivo") MultipartFile archivo) {
        final byte[] bytes;
        try {
            // Se lee en el hilo de la request (el hilo del pool no tiene acceso al MultipartFile).
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el archivo CSV.");
        }
        AsistenciaImportJob job = jobRegistry.crear();
        // El usuario se captura aquí (el hilo async no tiene SecurityContext).
        asyncRunner.ejecutarPreview(job, periodo, bytes, archivo.getOriginalFilename(), currentUser());
        return new ApiResponse<>("OK", "Validación iniciada", Map.of("jobId", job.getJobId()));
    }

    /**
     * Opción B — Progreso de CUALQUIER job de import (genérico: validar o confirmar). Polling.
     * Endpoint de dominio general de la importación: {@code GET /import/job/{jobId}}.
     */
    @Operation(
            summary = "Progreso de un job de import (validar/confirmar) — polling",
            description = "Devuelve { estado, porcentaje, fase, resultado?, error? }. El cliente consulta "
                    + "cada ~700 ms hasta estado COMPLETADO o ERROR.")
    @GetMapping("/job/{jobId}")
    public ApiResponse<AsistenciaImportJobDto> jobEstado(@PathVariable String jobId) {
        AsistenciaImportJob job = jobRegistry.get(jobId);
        if (job == null) {
            throw new NegocioException("Job no encontrado o expirado. Vuelva a iniciar la operación.");
        }
        return new ApiResponse<>("OK", "Estado del job", job.toDto());
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "SISTEMA";
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

    /**
     * Opción B (recomendada) — Confirma la importación en un job ASÍNCRONO y responde el {@code jobId}
     * de inmediato. El progreso (por empleado) se consulta por polling en {@code GET /import/job/{jobId}}.
     * El SecurityContext se propaga al hilo del job (autorización por rol + usuario intactos).
     */
    @Operation(
            summary = "Confirmación asíncrona (recomendada) — inicia un job y devuelve jobId",
            description = "Materializa la asistencia en un job asíncrono. Responde al instante con "
                    + "{ jobId }; el progreso se consulta por polling en GET /import/job/{jobId}.")
    @PostMapping("/{importacionId}/confirm/async")
    public ApiResponse<Map<String, String>> confirmarAsync(
            @PathVariable Long importacionId,
            @RequestBody(required = false) AsistenciaImportConfirmRequest request) {
        AsistenciaImportJob job = jobRegistry.crear();
        asyncRunner.ejecutarConfirmar(job, importacionId, request);
        return new ApiResponse<>("OK", "Confirmación iniciada", Map.of("jobId", job.getJobId()));
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

    /**
     * Opción B (recomendada) — "Ejecutar cálculo" en un job ASÍNCRONO; responde el {@code jobId} de
     * inmediato. El progreso (por cabecera) se consulta por polling en {@code GET /import/job/{jobId}}.
     */
    @Operation(
            summary = "Ejecutar cálculo asíncrono (recomendado) — inicia un job y devuelve jobId",
            description = "Valida las cabeceras (promueve a VALIDADA + recalcula) en un job asíncrono. "
                    + "Responde al instante con { jobId }; el progreso se consulta en GET /import/job/{jobId}.")
    @PostMapping("/{importacionId}/validar-cabeceras/async")
    public ApiResponse<Map<String, String>> validarCabecerasAsync(@PathVariable Long importacionId) {
        AsistenciaImportJob job = jobRegistry.crear();
        asyncRunner.ejecutarValidarCabeceras(job, importacionId);
        return new ApiResponse<>("OK", "Cálculo iniciado", Map.of("jobId", job.getJobId()));
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
