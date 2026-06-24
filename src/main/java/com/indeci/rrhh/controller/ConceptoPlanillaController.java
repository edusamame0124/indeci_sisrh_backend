package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ConceptoHistorialDto;
import com.indeci.rrhh.dto.ConceptoNuevaVersionDto;
import com.indeci.rrhh.dto.ConceptoPlanillaDto;
import com.indeci.rrhh.dto.ConceptoPlanillaResponseDto;
import com.indeci.rrhh.service.ConceptoPlanillaService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/concepto-planilla")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class ConceptoPlanillaController {

    private final ConceptoPlanillaService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> guardar(@RequestBody ConceptoPlanillaDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Concepto registrado", null);
    }

    @GetMapping
    public ApiResponse<List<ConceptoPlanillaResponseDto>> listar() {
        // Catálogo de gestión: incluye BORRADOR/EN_REVISION (no solo ACTIVO=1).
        return new ApiResponse<>("OK", "Lista conceptos", service.listarCatalogo());
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody ConceptoPlanillaDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Concepto actualizado", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Concepto eliminado", null);
    }

    // ============================================================
    // SPEC_CONCEPTOS_PLANILLA P1 — transiciones de estado (§8/D1)
    // ============================================================

    @PostMapping("/{id}/enviar-revision")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> enviarRevision(@PathVariable Long id) {
        service.enviarRevision(id);
        return new ApiResponse<>("OK", "Concepto enviado a revisión", null);
    }

    @PostMapping("/{id}/activar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_APPROVE)
    public ApiResponse<Void> activar(@PathVariable Long id) {
        service.activar(id);
        return new ApiResponse<>("OK", "Concepto activado", null);
    }

    @PostMapping("/{id}/cerrar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_APPROVE)
    public ApiResponse<Void> cerrar(@PathVariable Long id) {
        service.cerrar(id);
        return new ApiResponse<>("OK", "Concepto cerrado", null);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize(SisrhSecurityExpressions.PLA_APPROVE)
    public ApiResponse<Void> anular(@PathVariable Long id) {
        service.anular(id);
        return new ApiResponse<>("OK", "Concepto anulado", null);
    }

    // ============================================================
    // SPEC_CONCEPTOS_PLANILLA P3 — historial + nueva versión vigente (§12)
    // ============================================================

    @GetMapping("/{id}/historial")
    public ApiResponse<ConceptoHistorialDto> historial(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Historial del concepto", service.historial(id));
    }

    @PostMapping("/{id}/nueva-version")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Long> nuevaVersion(
            @PathVariable Long id,
            @RequestBody ConceptoNuevaVersionDto body) {
        Long nuevaId = service.crearNuevaVersion(id, body.getFechaVigIni());
        return new ApiResponse<>("OK", "Nueva versión vigente creada", nuevaId);
    }
}
