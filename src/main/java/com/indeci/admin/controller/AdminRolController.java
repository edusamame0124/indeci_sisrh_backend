package com.indeci.admin.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.admin.dto.AdminRolResponse;
import com.indeci.admin.service.AdminMetadataService;
import com.indeci.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminRolController {

    private final AdminMetadataService metadataService;

    @GetMapping
    public ApiResponse<List<AdminRolResponse>> listar() {
        return new ApiResponse<>("OK", "Roles", metadataService.listRoles());
    }
}
