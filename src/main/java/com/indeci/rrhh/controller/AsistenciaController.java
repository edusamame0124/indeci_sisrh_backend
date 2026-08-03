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
import com.indeci.rrhh.service.asistencia.AsistenciaResumenPeriodoService;
import com.indeci.rrhh.service.asistencia.AsistenciaResumenPeriodoXlsxWriter;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.time.LocalDate;

/**
 * Asistencia (M04). La autorización se declara <b>por método</b>, no a nivel de clase:
 * {@code /mis-asistencias} es autoservicio del propio empleado y debe seguir abierto a
 * cualquier usuario autenticado, mientras el resto exige la familia {@code ASI_*}
 * (rol ASISTENCIA / PLANILLA), que sustituye al antiguo {@code EMP_*}.
 */
@RestController
@RequestMapping("/api/rrhh/asistencia")
@RequiredArgsConstructor
public class AsistenciaController {

    private final AsistenciaService service;
    private final AsistenciaPdfService pdfService;
    private final AsistenciaImportService importService;
    private final AsistenciaResumenPeriodoService resumenPeriodoService;
    private final AsistenciaResumenPeriodoXlsxWriter resumenPeriodoXlsxWriter;

    /** Consulta de asistencia por rango [fechaInicio, fechaFin] y filtros opcionales (DNI, nombre). */
    @GetMapping("/diaria")
    @PreAuthorize(SisrhSecurityExpressions.ASI_READ)
    public ApiResponse<Page<AsistenciaDiariaRowDto>> listarDiaria(
            @RequestParam LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin,
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10) Pageable pageable) {
        return new ApiResponse<>("OK", "Asistencia del rango",
                service.listarDiaria(fechaInicio, fechaFin, dni, q, pageable));
    }

    /**
     * Reporte de asistencia consolidado por período (XLSX, 1 fila por empleado x período),
     * formato institucional {@code docs/reporte_asistencia.xlsx}. Usa el mismo rango de la
     * "Consulta diaria de asistencia" — si el rango cruza meses, exporta un bloque por cada
     * período calendario tocado.
     */
    @GetMapping("/diaria/xlsx")
    @PreAuthorize(SisrhSecurityExpressions.ASI_READ)
    public ResponseEntity<byte[]> exportarResumenPeriodoXlsx(
            @RequestParam LocalDate fechaInicio,
            @RequestParam LocalDate fechaFin) {
        byte[] xlsx = resumenPeriodoXlsxWriter.generar(resumenPeriodoService.generar(fechaInicio, fechaFin));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("reporte_asistencia_" + fechaInicio + "_" + fechaFin + ".xlsx")
                .build());
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }

    /** Detalle diario de una importación (lote) — módulo de detalle del historial (solo lectura). */
    @GetMapping("/importacion/{importacionId}/diaria")
    @PreAuthorize(SisrhSecurityExpressions.ASI_READ)
    public ApiResponse<Page<AsistenciaDiariaRowDto>> listarPorImportacion(
            @PathVariable Long importacionId,
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipoDia,
            @PageableDefault(size = 25) Pageable pageable) {
        return new ApiResponse<>("OK", "Asistencia importada del lote",
                service.listarPorImportacion(importacionId, dni, q, tipoDia, pageable));
    }

    /** Autoservicio: el empleado consulta SU propia asistencia. No exige permisos ASI_*. */
    @GetMapping("/mis-asistencias")
    @PreAuthorize("isAuthenticated()")
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

    /** Autoservicio: PDF de la asistencia propia por rango (puede abarcar varios meses calendario). */
    @GetMapping("/mis-asistencias/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> misAsistenciasPdf(
            @RequestParam LocalDate fechaInicio,
            @RequestParam LocalDate fechaFin) {
        Long empleadoId = service.obtenerEmpleadoActual();
        byte[] pdf = pdfService.generarRango(empleadoId, fechaInicio, fechaFin);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("mis-asistencias-" + fechaInicio + "_" + fechaFin + ".pdf")
                .build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    /** Edición puntual de un día desde consulta diaria. */
    @PatchMapping("/diaria/{detalleId}")
    @PreAuthorize(SisrhSecurityExpressions.ASI_WRITE)
    public ApiResponse<AsistenciaDiariaRowDto> editarDia(
            @PathVariable Long detalleId,
            @RequestBody AsistenciaDiariaEditDto dto) {
        return new ApiResponse<>("OK", "Asistencia actualizada",
                service.editarDia(detalleId, dto));
    }

    @GetMapping("/{empleadoId}/{periodo}")
    @PreAuthorize(SisrhSecurityExpressions.ASI_READ)
    public ApiResponse<AsistenciaResponseDto> obtener(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        return new ApiResponse<>("OK", "Asistencia del período",
                service.obtener(empleadoId, periodo));
    }

    /** Recalcula tardanza (desde marcas + jornada vigente) y descuentos del empleado/periodo. */
    @PostMapping("/{empleadoId}/{periodo}/recalcular")
    @PreAuthorize(SisrhSecurityExpressions.ASI_WRITE)
    public ApiResponse<AsistenciaResponseDto> recalcular(
            @PathVariable Long empleadoId,
            @PathVariable String periodo) {
        importService.recalcularAsistencia(empleadoId, periodo);
        return new ApiResponse<>("OK", "Asistencia recalculada",
                service.obtener(empleadoId, periodo));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.ASI_WRITE)
    public ApiResponse<Void> guardar(@RequestBody AsistenciaGuardarDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Asistencia registrada", null);
    }

    @GetMapping("/{empleadoId}/{periodo}/pdf")
    @PreAuthorize(SisrhSecurityExpressions.ASI_READ)
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

    /**
     * Backfill ÚNICO (temporal) — sanea el histórico afectado por la falta de reconciliación
     * LABORAL/TARDANZA → VACACIONES antes de este fix. Reconcilia TODAS las papeletas de
     * Vacaciones ya APROBADAS contra las cabeceras activas actuales. Idempotente (seguro
     * reintentar). Gateado a SUPER_ADMIN por ser una operación masiva de una sola vez —
     * quitar este endpoint una vez ejecutado en cada ambiente.
     */
    @PostMapping("/backfill-reconciliacion-vacaciones")
    @PreAuthorize(SisrhSecurityExpressions.SUPER_ADMIN)
    public ApiResponse<Integer> backfillReconciliacionVacaciones() {
        int procesadas = service.backfillReconciliarVacacionesAprobadas();
        return new ApiResponse<>("OK", "Papeletas de vacaciones reconciliadas: " + procesadas, procesadas);
    }

    /**
     * Backfill ÚNICO (temporal, independiente del anterior) — corrige días ya persistidos mal
     * clasificados como LABORAL/OBSERVADO que en realidad son FERIADO según el catálogo oficial
     * (INDECI_FERIADO) y no tienen marcación real. Idempotente. Gateado a SUPER_ADMIN — quitar
     * este endpoint una vez ejecutado en cada ambiente.
     */
    @PostMapping("/backfill-feriados")
    @PreAuthorize(SisrhSecurityExpressions.SUPER_ADMIN)
    public ApiResponse<Integer> backfillFeriados() {
        int corregidos = service.backfillFeriadosMalClasificados();
        return new ApiResponse<>("OK", "Días de feriado corregidos: " + corregidos, corregidos);
    }
}
