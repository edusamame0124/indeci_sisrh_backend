package com.indeci.rrhh.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.indeci.security.auth.SisrhSecurityExpressions;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ExportArchivoResponseDto;
import com.indeci.rrhh.service.ExportLogService;

import lombok.RequiredArgsConstructor;

/**
 * B3 / M09 — Historial de exportaciones PLAME/MCPP. Base: {@code /api/rrhh/export-archivo}.
 */
@RestController
@RequestMapping("/api/rrhh/export-archivo")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.RPT_READ)
public class ExportArchivoController {

    private final ExportLogService exportLog;

    @GetMapping("/{periodo}")
    public ApiResponse<List<ExportArchivoResponseDto>> historial(@PathVariable String periodo) {
        return new ApiResponse<>("OK", "Historial de exportaciones del período",
                exportLog.historial(periodo));
    }
}
