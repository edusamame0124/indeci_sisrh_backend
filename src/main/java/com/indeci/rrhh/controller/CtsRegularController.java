package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.cts.CtsRegularRequestDto;
import com.indeci.rrhh.dto.cts.CtsRegularResultDto;
import com.indeci.rrhh.service.cts.CtsRegularCalculationService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cts-regular")
@RequiredArgsConstructor
public class CtsRegularController {

    private final CtsRegularCalculationService ctsRegularCalculationService;

    @PostMapping("/generar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_CTS_WRITE)
    public ApiResponse<CtsRegularResultDto> generar(@Valid @RequestBody CtsRegularRequestDto req) {
        CtsRegularResultDto result = ctsRegularCalculationService.generarCts(req.periodo(), req.regimenLaboralId());
        return new ApiResponse<>("OK", "CTS Regular generada con éxito", result);
    }

    @GetMapping("/reporte")
    @PreAuthorize(SisrhSecurityExpressions.PLA_CTS_READ)
    public ResponseEntity<Resource> descargarReporte(
            @RequestParam Long empleadoId,
            @RequestParam String periodo) {
        
        Resource pdf = ctsRegularCalculationService.generarReportePdf(empleadoId, periodo);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"CTS_REGULAR_" + periodo + "_" + empleadoId + ".pdf\"")
                .body(pdf);
    }
}
