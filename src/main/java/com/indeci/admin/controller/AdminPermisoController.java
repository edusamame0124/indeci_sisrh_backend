package com.indeci.admin.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.admin.dto.AdminPermisoResponse;
import com.indeci.admin.service.AdminMetadataService;
import com.indeci.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminPermisoController {

    private final AdminMetadataService metadataService;

    @GetMapping
    public ApiResponse<List<AdminPermisoResponse>> listar() {
        return new ApiResponse<>("OK", "Permisos", metadataService.listPermisos());
    }
}
