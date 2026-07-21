package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.AsistenciaDiariaEditDto;
import com.indeci.rrhh.dto.AsistenciaDiariaRowDto;
import com.indeci.rrhh.dto.AsistenciaGuardarDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.service.AsistenciaImportService;
import com.indeci.rrhh.service.AsistenciaService;
import com.indeci.rrhh.service.AsistenciaPdfService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/rrhh/asistencia")
@RequiredArgsConstructor
//@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class AsistenciaController {

    private final AsistenciaService service;
    private final AsistenciaPdfService pdfService;
    private final AsistenciaImportService importService;

    /** Consulta de asistencia por rango [fechaInicio, fechaFin] y filtros opcionales (DNI, nombre). */
    @GetMapping("/diaria")
    public ApiResponse<Page<AsistenciaDiariaRowDto>> listarDiaria(
            @RequestParam LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin,
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10) Pageable pageable) {
        return new ApiResponse<>("OK", "Asistencia del rango",
                service.listarDiaria(fechaInicio, fechaFin, dni, q, pageable));
    }
    
    /** Detalle diario de una importación (lote) — módulo de detalle del historial (solo lectura). */
    @GetMapping("/importacion/{importacionId}/diaria")
    public ApiResponse<Page<AsistenciaDiariaRowDto>> listarPorImportacion(
            @PathVariable Long importacionId,
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipoDia,
            @PageableDefault(size = 25) Pageable pageable) {
        return new ApiResponse<>("OK", "Asistencia importada del lote",
                service.listarPorImportacion(importacionId, dni, q, tipoDia, pageable));
    }

    @GetMapping("/mis-asistencias")
    public ApiResponse<Page<AsistenciaDiariaRowDto>> misAsistencias(
            @RequestParam LocalDate fechaInicio,
            @RequestParam LocalDate fechaFin,
            @PageableDefault(size = 100) Pageable pageable) {

        return new ApiResponse<>(
                "OK",
                "Mis asistencias",
                service.misAsistencias(
                        fechaInicio,
                        fechaFin,
                        pageable));
    }

    /** Edición puntual de un día desde consulta diaria. */
    @PatchMapping("/diaria/{detalleId}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<AsistenciaDiariaRowDto> editarDia(
            @PathVariable Long detalleId,
            @RequestBody AsistenciaDiariaEditDto dto) {
        return new ApiResponse<>("OK", "Asistencia actualizada",
                service.editarDia(detalleId, dto));
    }

    @GetMapping("/{empleadoId}/{periodo}")
    public ApiResponse<AsistenciaResponseDto> obtener(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        return new ApiResponse<>("OK", "Asistencia del período",
                service.obtener(empleadoId, periodo));
    }

    /** Recalcula tardanza (desde marcas + jornada vigente) y descuentos del empleado/periodo. */
    @PostMapping("/{empleadoId}/{periodo}/recalcular")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<AsistenciaResponseDto> recalcular(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        importService.recalcularAsistencia(empleadoId, periodo);
        return new ApiResponse<>("OK", "Asistencia recalculada",
                service.obtener(empleadoId, periodo));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> guardar(@RequestBody AsistenciaGuardarDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Asistencia registrada", null);
    }

    @GetMapping("/{empleadoId}/{periodo}/pdf")
    @PreAuthorize(SisrhSecurityExpressions.EMP_READ)
    public ResponseEntity<byte[]> pdf(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        byte[] pdf = pdfService.generar(empleadoId, periodo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("asistencia-" + empleadoId + "-" + periodo + ".pdf")
                .build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
