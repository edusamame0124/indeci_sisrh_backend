package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.security.auth.SisrhSecurityExpressions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.PlamePreviewDto;
import com.indeci.rrhh.service.ExportLogService;
import com.indeci.rrhh.service.PlameService;
import com.indeci.rrhh.service.PlameService.PlameArchivo;

import lombok.RequiredArgsConstructor;

/**
 * B3 / M09 — Descarga de los archivos PLAME / PDT 601 (.rem, .jor, .snl).
 * Base: {@code /api/rrhh/plame}. Cada descarga se registra en el log de exportación.
 */
@RestController
@RequestMapping("/api/rrhh/plame")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.RPT_READ)
public class PlameController {

    private final PlameService plameService;
    private final ExportLogService exportLog;

    @GetMapping("/{periodo}/rem")
    public ResponseEntity<byte[]> descargarRem(@PathVariable String periodo) {
        PlameArchivo a = plameService.generarRem(periodo);
        exportLog.registrar(periodo, "PLAME_REM", a.nombreArchivo(), a.contenido(),
                a.totalIngresos(), a.totalDescuentos());
        return ExportHttp.textoPlano(a.nombreArchivo(), a.contenido());
    }

    @GetMapping("/{periodo}/jor")
    public ResponseEntity<byte[]> descargarJor(@PathVariable String periodo) {
        PlameArchivo a = plameService.generarJor(periodo);
        exportLog.registrar(periodo, "PLAME_JOR", a.nombreArchivo(), a.contenido(), null, null);
        return ExportHttp.textoPlano(a.nombreArchivo(), a.contenido());
    }

    @GetMapping("/{periodo}/snl")
    public ResponseEntity<byte[]> descargarSnl(@PathVariable String periodo) {
        PlameArchivo a = plameService.generarSnl(periodo);
        exportLog.registrar(periodo, "PLAME_SNL", a.nombreArchivo(), a.contenido(), null, null);
        return ExportHttp.textoPlano(a.nombreArchivo(), a.contenido());
    }

    @GetMapping("/{periodo}/zip")
    public ResponseEntity<byte[]> descargarZip(@PathVariable String periodo) {
        PlameArchivo rem = plameService.generarRem(periodo);
        PlameArchivo jor = plameService.generarJor(periodo);
        PlameArchivo snl = plameService.generarSnl(periodo);
        exportLog.registrar(periodo, "PLAME_REM", rem.nombreArchivo(), rem.contenido(),
                rem.totalIngresos(), rem.totalDescuentos());
        exportLog.registrar(periodo, "PLAME_JOR", jor.nombreArchivo(), jor.contenido(), null, null);
        exportLog.registrar(periodo, "PLAME_SNL", snl.nombreArchivo(), snl.contenido(), null, null);

        Map<String, String> archivos = new LinkedHashMap<>();
        archivos.put(rem.nombreArchivo(), rem.contenido());
        archivos.put(jor.nombreArchivo(), jor.contenido());
        archivos.put(snl.nombreArchivo(), snl.contenido());
        return ExportHttp.zip("plame-" + periodo.replace("-", "") + ".zip", archivos);
    }

    @GetMapping("/{periodo}/preview")
    public ApiResponse<PlamePreviewDto> preview(@PathVariable String periodo) {
        return new ApiResponse<>("OK", "Resumen PLAME del período", plameService.preview(periodo));
    }
}
