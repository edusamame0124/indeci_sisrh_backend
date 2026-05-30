package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ConciliacionAirhspDto;
import com.indeci.rrhh.dto.ConciliacionAirhspResponseDto;
import com.indeci.rrhh.dto.ConciliacionRevisionDto;
import com.indeci.rrhh.service.ConciliacionAirhspService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/conciliacion-airhsp")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.RPT_READ)
public class ConciliacionAirhspController {

    private final ConciliacionAirhspService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.RPT_WRITE)
    public ApiResponse<Void> registrar(@RequestBody ConciliacionAirhspDto dto) {
        service.registrar(dto);
        return new ApiResponse<>("OK", "Conciliación AIRHSP registrada", null);
    }

    @GetMapping("/periodo/{periodoPlanillaId}")
    public ApiResponse<List<ConciliacionAirhspResponseDto>> listarPorPeriodo(
            @PathVariable Long periodoPlanillaId) {
        return new ApiResponse<>("OK", "Conciliaciones del periodo",
                service.listarPorPeriodo(periodoPlanillaId));
    }

    @PutMapping("/{id}/revisar")
    @PreAuthorize(SisrhSecurityExpressions.RPT_WRITE)
    public ApiResponse<Void> revisar(
            @PathVariable Long id, @RequestBody ConciliacionRevisionDto dto) {
        service.revisar(id, dto);
        return new ApiResponse<>("OK", "Conciliación revisada", null);
    }
}
