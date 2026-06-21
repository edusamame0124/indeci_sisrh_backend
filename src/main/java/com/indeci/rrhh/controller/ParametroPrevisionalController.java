package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.previsional.*;
import com.indeci.rrhh.service.ParametroPrevisionalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints para configuración de parámetros previsionales AFP/ONP.
 * Requiere permiso PLA_WRITE (administradores de planilla/RRHH).
 */
@RestController
@RequestMapping("/api/rrhh/previsional")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('PLA_WRITE')")
public class ParametroPrevisionalController {

    private final ParametroPrevisionalService service;

    // ── KPI ──────────────────────────────────────────────────

    @GetMapping("/kpi")
    public ApiResponse<PrevisionalKpiDto> kpi() {
        return new ApiResponse<>("OK", "KPI previsional", service.kpi());
    }

    // ── Catálogo AFP ─────────────────────────────────────────

    @GetMapping("/afp/catalogo")
    public ApiResponse<List<AfpCatalogoDto>> afpCatalogo() {
        return new ApiResponse<>("OK", "Catálogo AFP", service.listarAfpCatalogo());
    }

    // ── Parámetros AFP ───────────────────────────────────────

    @GetMapping("/afp/parametros")
    public ApiResponse<List<AfpParametroDto>> afpParametros(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "false") boolean incluirAnulados) {
        return new ApiResponse<>("OK", "Parámetros AFP",
                service.listarAfpParametros(estado, incluirAnulados));
    }

    @PostMapping("/afp/parametros")
    public ApiResponse<AfpParametroDto> crearAfpParametro(@Valid @RequestBody AfpParametroInputDto input) {
        return new ApiResponse<>("OK", "Vigencia AFP creada",
                service.crearAfpParametro(input, usuarioActual()));
    }

    @PutMapping("/afp/parametros/{id}")
    public ApiResponse<AfpParametroDto> editarAfpParametro(
            @PathVariable Long id,
            @Valid @RequestBody AfpParametroInputDto input) {
        return new ApiResponse<>("OK", "Vigencia AFP actualizada",
                service.editarAfpParametro(id, input, usuarioActual()));
    }

    @PostMapping("/afp/parametros/{id}/cerrar")
    public ApiResponse<Void> cerrarAfpVigencia(@PathVariable Long id) {
        service.cerrarAfpVigencia(id, usuarioActual());
        return new ApiResponse<>("OK", "Vigencia AFP cerrada", null);
    }

    @PostMapping("/afp/parametros/{id}/eliminar")
    public ApiResponse<Void> eliminarAfpVigencia(
            @PathVariable Long id,
            @Valid @RequestBody AnularVigenciaRequestDto request) {
        service.anularAfpVigencia(id, request.getMotivo(), usuarioActual());
        return new ApiResponse<>("OK",
                "Vigencia AFP anulada correctamente. Ya no será considerada por el motor de planilla.", null);
    }

    @PostMapping("/afp/parametros/{id}/duplicar")
    public ApiResponse<AfpParametroDto> duplicarAfpVigencia(
            @PathVariable Long id,
            @Valid @RequestBody DuplicarVigenciaRequestDto request) {
        return new ApiResponse<>("OK", "Vigencia AFP duplicada",
                service.duplicarAfpVigencia(id, request, usuarioActual()));
    }

    @PostMapping("/onp/parametros/{id}/duplicar")
    public ApiResponse<OnpParametroDto> duplicarOnpVigencia(
            @PathVariable Long id,
            @Valid @RequestBody DuplicarVigenciaRequestDto request) {
        return new ApiResponse<>("OK", "Vigencia ONP duplicada",
                service.duplicarOnpVigencia(id, request, usuarioActual()));
    }

    // ── Parámetros ONP ───────────────────────────────────────

    @GetMapping("/onp/parametros")
    public ApiResponse<List<OnpParametroDto>> onpParametros(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "false") boolean incluirAnulados) {
        return new ApiResponse<>("OK", "Parámetros ONP",
                service.listarOnpParametros(estado, incluirAnulados));
    }

    @PostMapping("/onp/parametros")
    public ApiResponse<OnpParametroDto> crearOnpParametro(@Valid @RequestBody OnpParametroInputDto input) {
        return new ApiResponse<>("OK", "Vigencia ONP creada",
                service.crearOnpParametro(input, usuarioActual()));
    }

    @PutMapping("/onp/parametros/{id}")
    public ApiResponse<OnpParametroDto> editarOnpParametro(
            @PathVariable Long id,
            @Valid @RequestBody OnpParametroInputDto input) {
        return new ApiResponse<>("OK", "Vigencia ONP actualizada",
                service.editarOnpParametro(id, input, usuarioActual()));
    }

    @PostMapping("/onp/parametros/{id}/cerrar")
    public ApiResponse<Void> cerrarOnpVigencia(@PathVariable Long id) {
        service.cerrarOnpVigencia(id, usuarioActual());
        return new ApiResponse<>("OK", "Vigencia ONP cerrada", null);
    }

    @PostMapping("/onp/parametros/{id}/eliminar")
    public ApiResponse<Void> eliminarOnpVigencia(
            @PathVariable Long id,
            @Valid @RequestBody AnularVigenciaRequestDto request) {
        service.anularOnpVigencia(id, request.getMotivo(), usuarioActual());
        return new ApiResponse<>("OK",
                "Vigencia ONP anulada correctamente. Ya no será considerada por el motor de planilla.", null);
    }

    // ── Resolver ─────────────────────────────────────────────

    @GetMapping("/resolver")
    public ApiResponse<PrevisionalResolverResultDto> resolver(
            @RequestParam Long empleadoId,
            @RequestParam String periodo) {
        return new ApiResponse<>("OK", "Parámetro resuelto",
                service.resolver(empleadoId, periodo));
    }

    // ── Historial / Auditoría ────────────────────────────────

    @GetMapping("/historial")
    public ApiResponse<List<PrevisionalLogDto>> historial() {
        return new ApiResponse<>("OK", "Historial previsional", service.historial());
    }

    @GetMapping("/auditoria")
    public ApiResponse<List<PrevisionalLogDto>> auditoria() {
        return new ApiResponse<>("OK", "Auditoría previsional", service.historial());
    }

    // ── Helper ───────────────────────────────────────────────

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "SISTEMA";
    }
}
