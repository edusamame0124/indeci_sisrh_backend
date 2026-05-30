package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.security.auth.SisrhSecurityExpressions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.McppPlanillaDisponibleDto;
import com.indeci.rrhh.service.ExportLogService;
import com.indeci.rrhh.service.McppService;
import com.indeci.rrhh.service.McppService.McppArchivo;

import lombok.RequiredArgsConstructor;

/**
 * B3 / M14 — MCPP Web (PLL*.TXT). Base: {@code /api/rrhh/mcpp}.
 * Descargar un tipo emite el NRO_PLANILLA (correlativo) y registra la exportación.
 */
@RestController
@RequestMapping("/api/rrhh/mcpp")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.RPT_READ)
public class McppController {

    private final McppService mcppService;
    private final ExportLogService exportLog;

    /** Planillas disponibles para exportar en el período (resumen, no emite correlativo). */
    @GetMapping("/{periodo}")
    public ApiResponse<List<McppPlanillaDisponibleDto>> listar(@PathVariable String periodo) {
        return new ApiResponse<>("OK", "Planillas MCPP disponibles",
                mcppService.listarDisponibles(periodo));
    }

    /** Descarga el PLL*.TXT de un tipo (01 SERVIR, 03 CAS, 12 Judiciales). */
    @GetMapping("/{periodo}/{tipo}")
    public ResponseEntity<byte[]> descargarTipo(
            @PathVariable String periodo, @PathVariable String tipo) {
        McppArchivo a = mcppService.generar(periodo, tipo);
        exportLog.registrar(periodo, "MCPP_" + tipo, a.nombreArchivo(), a.contenido(),
                a.totalIngresos(), a.totalDescuentos());
        return ExportHttp.textoPlano(a.nombreArchivo(), a.contenido());
    }

    /** Descarga un ZIP con todas las planillas disponibles del período. */
    @GetMapping("/{periodo}/zip")
    public ResponseEntity<byte[]> descargarZip(@PathVariable String periodo) {
        Map<String, String> archivos = new LinkedHashMap<>();
        for (McppPlanillaDisponibleDto disp : mcppService.listarDisponibles(periodo)) {
            McppArchivo a = mcppService.generar(periodo, disp.getTipoPlanilla());
            exportLog.registrar(periodo, "MCPP_" + disp.getTipoPlanilla(), a.nombreArchivo(),
                    a.contenido(), a.totalIngresos(), a.totalDescuentos());
            archivos.put(a.nombreArchivo(), a.contenido());
        }
        return ExportHttp.zip("mcpp-" + periodo.replace("-", "") + ".zip", archivos);
    }
}
