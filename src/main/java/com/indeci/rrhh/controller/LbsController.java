package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.lbs.LbsGenerarRequestDto;
import com.indeci.rrhh.dto.lbs.LbsResultDto;
import com.indeci.rrhh.service.lbs.LbsCalculationService;
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
@RequestMapping("/api/v1/lbs")
@RequiredArgsConstructor
public class LbsController {

    private final LbsCalculationService lbsCalculationService;

    @PostMapping("/generar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_LBS_WRITE)
    public ApiResponse<LbsResultDto> generar(@Valid @RequestBody LbsGenerarRequestDto req) {
        LbsResultDto result = lbsCalculationService.generarLbs(req.periodo(), req.regimenLaboralId());
        return new ApiResponse<>("OK", "LBS generada con éxito", result);
    }

    @GetMapping("/reporte")
    @PreAuthorize(SisrhSecurityExpressions.PLA_LBS_READ)
    public ResponseEntity<Resource> descargarReporte(
            @RequestParam Long empleadoId,
            @RequestParam String periodo) {
        
        Resource pdf = lbsCalculationService.generarReportePdf(empleadoId, periodo);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"LBS_" + periodo + "_" + empleadoId + ".pdf\"")
                .body(pdf);
    }
}
