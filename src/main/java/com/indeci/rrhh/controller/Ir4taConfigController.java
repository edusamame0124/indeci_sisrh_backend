package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ir4ta.*;
import com.indeci.rrhh.service.Ir4taConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints para la configuración anual de Rentas de 4ta Categoría CAS.
 * Base normativa: TUO LIR Art. 33 inc. e) · D.S. 122-94-EF · SUNAT 3042.
 */
@RestController
@RequestMapping("/api/rrhh/ir4ta/config")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('PLA_WRITE')")
public class Ir4taConfigController {

    private final Ir4taConfigService service;

    @GetMapping
    public ApiResponse<List<Ir4taConfigDto>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "false") boolean incluirAnulados) {
        return new ApiResponse<>("OK", "Configuraciones IR4ta", service.listar(estado, incluirAnulados));
    }

    @PostMapping
    public ApiResponse<Ir4taConfigDto> crear(@Valid @RequestBody Ir4taConfigInputDto input) {
        return new ApiResponse<>("OK", "Configuración IR4ta creada", service.crear(input, usuarioActual()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Ir4taConfigDto> editar(
            @PathVariable Long id,
            @Valid @RequestBody Ir4taConfigInputDto input) {
        return new ApiResponse<>("OK", "Configuración IR4ta actualizada", service.editar(id, input, usuarioActual()));
    }

    @PostMapping("/{id}/publicar")
    public ApiResponse<Ir4taConfigDto> publicar(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Vigencia publicada", service.publicar(id, usuarioActual()));
    }

    @PostMapping("/{id}/cerrar")
    public ApiResponse<Void> cerrar(@PathVariable Long id) {
        service.cerrar(id, usuarioActual());
        return new ApiResponse<>("OK", "Vigencia IR4ta cerrada", null);
    }

    @PostMapping("/{id}/duplicar")
    public ApiResponse<Ir4taConfigDto> duplicar(
            @PathVariable Long id,
            @Valid @RequestBody Ir4taConfigDuplicarInputDto req) {
        return new ApiResponse<>("OK", "Vigencia IR4ta duplicada", service.duplicar(id, req, usuarioActual()));
    }

    @PostMapping("/{id}/eliminar")
    public ApiResponse<Void> anular(
            @PathVariable Long id,
            @Valid @RequestBody Ir4taConfigAnularInputDto req) {
        service.anular(id, req, usuarioActual());
        return new ApiResponse<>("OK",
            "Vigencia anulada correctamente. Ya no será considerada por el motor de planilla.", null);
    }

    @GetMapping("/resolver")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('PLA_WRITE') or hasAuthority('PLA_READ')")
    public ApiResponse<Ir4taResolverResultDto> resolver(@RequestParam String periodo) {
        return new ApiResponse<>("OK", "Resolución IR4ta", service.resolver(periodo));
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SISTEMA";
    }
}
