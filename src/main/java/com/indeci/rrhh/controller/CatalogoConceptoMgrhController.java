package com.indeci.rrhh.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.CatalogoConceptoMgrhDto;
import com.indeci.rrhh.service.CatalogoConceptoMgrhService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * SPEC_HOMOLOGACION_MGRH §E (D4) — API paginada de consulta del catálogo MGRH/MEF.
 *
 * <p>Solo lectura, reutilizable (la consume el buscador de la pestaña "Homologación
 * MGRH / MEF"). Defaults: {@code soloSeleccionables=true} (excluye GASTOS POR ENCARGO)
 * y {@code soloVigentes=true} (versión anual más reciente).</p>
 */
@RestController
@RequestMapping("/api/rrhh/catalogo-mgrh")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class CatalogoConceptoMgrhController {

    private static final int SIZE_MAX = 200;

    private final CatalogoConceptoMgrhService service;

    @GetMapping
    public ApiResponse<Page<CatalogoConceptoMgrhDto>> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String tipoLocal,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false) String detalle,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "false") boolean soloActivos,
            @RequestParam(defaultValue = "true") boolean soloSeleccionables,
            @RequestParam(defaultValue = "true") boolean soloVigentes,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), SIZE_MAX);
        CatalogoConceptoMgrhService.Filtros filtros = CatalogoConceptoMgrhService.Filtros.builder()
                .texto(texto)
                .tipoLocal(tipoLocal)
                .codigo(codigo)
                .tipo(tipo)
                .descripcion(descripcion)
                .detalle(detalle)
                .estado(estado)
                .soloActivos(soloActivos)
                .soloSeleccionables(soloSeleccionables)
                .soloVigentes(soloVigentes)
                .build();

        return new ApiResponse<>("OK",
                "Catálogo MGRH/MEF",
                service.buscar(filtros, PageRequest.of(Math.max(page, 0), safeSize)));
    }
}
