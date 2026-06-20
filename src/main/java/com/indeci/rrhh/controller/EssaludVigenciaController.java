package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.essalud.*;
import com.indeci.rrhh.service.EssaludVigenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints para la configuración de parámetros EsSalud/EPS por vigencia.
 * Base normativa: D.S. 009-97-SA — aporte empleador 9%.
 */
@RestController
@RequestMapping("/api/rrhh/essalud")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('PLA_WRITE')")
public class EssaludVigenciaController {

    private final EssaludVigenciaService service;

    @GetMapping("/vigencias")
    public ApiResponse<List<EssaludVigenciaDto>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "false") boolean incluirAnulados) {
        return new ApiResponse<>("OK", "Vigencias EsSalud", service.listar(estado, incluirAnulados));
    }

    @PostMapping("/vigencias")
    public ApiResponse<EssaludVigenciaDto> crear(@Valid @RequestBody EssaludVigenciaInputDto input) {
        return new ApiResponse<>("OK", "Vigencia EsSalud creada", service.crear(input, usuarioActual()));
    }

    @PutMapping("/vigencias/{id}")
    public ApiResponse<EssaludVigenciaDto> editar(
            @PathVariable Long id,
            @Valid @RequestBody EssaludVigenciaInputDto input) {
        return new ApiResponse<>("OK", "Vigencia EsSalud actualizada", service.editar(id, input, usuarioActual()));
    }

    @PostMapping("/vigencias/{id}/cerrar")
    public ApiResponse<Void> cerrar(@PathVariable Long id) {
        service.cerrar(id, usuarioActual());
        return new ApiResponse<>("OK", "Vigencia EsSalud cerrada", null);
    }

    @PostMapping("/vigencias/{id}/duplicar")
    public ApiResponse<EssaludVigenciaDto> duplicar(
            @PathVariable Long id,
            @Valid @RequestBody EssaludDuplicarInputDto req) {
        return new ApiResponse<>("OK", "Vigencia EsSalud duplicada", service.duplicar(id, req, usuarioActual()));
    }

    @PostMapping("/vigencias/{id}/eliminar")
    public ApiResponse<Void> anular(
            @PathVariable Long id,
            @Valid @RequestBody EssaludAnularInputDto req) {
        service.anular(id, req, usuarioActual());
        return new ApiResponse<>("OK", "Vigencia EsSalud anulada correctamente. Ya no será considerada por el motor de planilla.", null);
    }

    @GetMapping("/resolver")
    public ApiResponse<EssaludResolverResultDto> resolver(
            @RequestParam Long empleadoId,
            @RequestParam String periodo,
            @RequestParam(required = false) Boolean tieneEps) {
        return new ApiResponse<>("OK", "Resolución EsSalud", service.resolver(empleadoId, periodo, tieneEps));
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SISTEMA";
    }
}
