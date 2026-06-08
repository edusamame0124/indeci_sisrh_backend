package com.indeci.rrhh.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.rrhh.dto.ExportHistorialDto;
import com.indeci.rrhh.entity.ExportArchivo;
import com.indeci.rrhh.repository.ExportArchivoRepository;
import com.indeci.rrhh.service.PlanillaXlsxService;

import lombok.RequiredArgsConstructor;

/**
 * B1 — Endpoints de export XLSX consolidado y su historial.
 *
 * <pre>
 *   GET /api/rrhh/planilla/export/xlsx?periodo=2026-06   → descarga el XLSX
 *   GET /api/rrhh/planilla/export/historial?periodo=2026-06 → lista exports anteriores
 * </pre>
 */
@RestController
@RequestMapping("/api/rrhh/planilla/export")
@RequiredArgsConstructor
public class ExportXlsxController {

    private final PlanillaXlsxService xlsxService;
    private final ExportArchivoRepository exportRepo;
    private final com.indeci.rrhh.service.PlanillaConsolidadaCasService casConsolidadaService;

    /**
     * Genera y descarga la Planilla Única Consolidada XLSX para el período.
     * Requiere el mismo permiso que ver movimientos de planilla.
     */
    @GetMapping("/xlsx")
    @PreAuthorize("hasAnyAuthority('PLA_READ','PLA_WRITE','ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> descargarXlsx(@RequestParam String periodo) {
        byte[] bytes = xlsxService.generarYRegistrar(periodo);
        String filename = "planilla_consolidada_" + periodo + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(bytes);
    }

    /**
     * P0 — Genera y descarga la Planilla CAS Consolidada (spec
     * SPEC_PLANILLA_CAS_EXPORT_COLUMNS). Contiene datos bancarios y
     * remunerativos → requiere PLA_WRITE (PLA_READ NO autoriza).
     */
    @GetMapping("/cas-consolidada")
    @PreAuthorize("hasAnyAuthority('PLA_WRITE','ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<byte[]> descargarCasConsolidada(@RequestParam String periodo) {
        byte[] bytes = casConsolidadaService.generarYRegistrar(periodo);
        String filename = "planilla-cas-consolidada-" + periodo + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(bytes);
    }

    /**
     * Lista el historial de exports XLSX para el período (más reciente primero).
     * Útil para que el analista sepa cuántas veces se generó y con qué hash.
     */
    @GetMapping("/historial")
    @PreAuthorize("hasAnyAuthority('PLA_READ','PLA_WRITE','ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public List<ExportHistorialDto> historial(@RequestParam String periodo) {
        return exportRepo.findByPeriodoOrderByFechaGeneradoDesc(periodo)
                .stream()
                .map(e -> new ExportHistorialDto(
                        e.getId(), e.getPeriodo(), e.getTipoArchivo(),
                        e.getNombreArchivo(), e.getNroLineas(),
                        e.getFechaGenerado(), e.getHashSha256()))
                .toList();
    }
}
