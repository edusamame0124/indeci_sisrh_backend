package com.indeci.sistema.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.security.auth.SisrhSecurityExpressions;
import com.indeci.sistema.dto.GdrDirectoryUserResponse;
import com.indeci.sistema.service.GdrDirectoryService;

import lombok.RequiredArgsConstructor;

/**
 * Directorio de usuarios GDR (sistema 'rendimiento') para integración con GDR.
 * Protegido por el permiso dedicado GDR_DIRECTORY_READ (cuenta de servicio
 * svc-gdr, menor privilegio).
 */
@RestController
@RequestMapping("/api/sistemas/rendimiento")
@RequiredArgsConstructor
public class SistemaDirectorioController {

    private final GdrDirectoryService gdrDirectoryService;

    @GetMapping("/usuarios")
    @PreAuthorize(SisrhSecurityExpressions.GDR_DIRECTORY_READ)
    public ApiResponse<List<GdrDirectoryUserResponse>> usuariosGdr(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "rol", required = false, defaultValue = "GDR_USUARIO") String rol) {
        return new ApiResponse<>(
                "OK",
                "Directorio GDR_USUARIO",
                gdrDirectoryService.buscarUsuariosGdr(q, rol));
    }
}
