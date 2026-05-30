package com.indeci.admin.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.admin.service.AdminAuditoriaService;
import com.indeci.audit.entity.Auditoria;
import com.indeci.security.auth.SisrhSecurityExpressions;
import com.indeci.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/auditoria")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.ADM_AUDIT)
public class AdminAuditoriaController {

    private final AdminAuditoriaService auditoriaService;

    @GetMapping
    public ApiResponse<org.springframework.data.domain.Page<Auditoria>> consultar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(Math.max(page, 0), safeSize);
        var result = auditoriaService.query(usuario, accion, fechaDesde, fechaHasta, ip, pageable);
        return new ApiResponse<>("OK", "Consulta auditoría", result);
    }
}
