package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.JornadaRegimenDto;
import com.indeci.rrhh.service.JornadaRegimenService;
import com.indeci.security.auth.SisrhSecurityExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración de jornada y tolerancias por régimen laboral (M04).
 */
@RestController
@RequestMapping("/api/rrhh/parametros/jornada")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
public class JornadaRegimenController {

    private final JornadaRegimenService service;

    @GetMapping
    public ApiResponse<java.util.List<JornadaRegimenDto>> listar() {
        return new ApiResponse<>("OK", "Configuraciones de jornada", service.listar());
    }

    @GetMapping("/{regimenLaboralId}")
    public ApiResponse<JornadaRegimenDto> obtener(@PathVariable Long regimenLaboralId) {
        return new ApiResponse<>("OK",
                "Configuración de jornada del régimen",
                service.obtener(regimenLaboralId));
    }

    @PostMapping
    public ApiResponse<JornadaRegimenDto> guardar(@RequestBody JornadaRegimenDto dto) {
        return new ApiResponse<>("OK",
                "Configuración exitosa",
                service.guardar(dto));
    }

    @DeleteMapping("/{regimenLaboralId}")
    public ApiResponse<Void> eliminar(@PathVariable Long regimenLaboralId) {
        service.eliminar(regimenLaboralId);
        return new ApiResponse<>("OK", "Configuración eliminada", null);
    }
}
