package com.indeci.admin.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.admin.dto.SistemaResponse;
import com.indeci.admin.dto.SistemaRolDto;
import com.indeci.common.dto.ApiResponse;
import com.indeci.exception.NegocioException;
import com.indeci.security.auth.SisrhSecurityExpressions;
import com.indeci.sistema.entity.SistemaRol;
import com.indeci.sistema.repository.SistemaRepository;
import com.indeci.sistema.repository.SistemaRolRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/sistemas")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.SUPER_ADMIN)
public class AdminSistemaController {

    private final SistemaRepository sistemaRepository;
    private final SistemaRolRepository sistemaRolRepository;

    @GetMapping
    public ApiResponse<List<SistemaResponse>> listar() {
        List<SistemaResponse> sistemas = sistemaRepository.findByActivoOrderByOrdenAsc(1)
                .stream()
                .map(s -> new SistemaResponse(
                        s.getCodigo(),
                        s.getNombre(),
                        s.getDescripcion(),
                        s.getIcono(),
                        s.getOrden(),
                        s.getActivo()))
                .toList();
        return new ApiResponse<>("OK", "Sistemas", sistemas);
    }

    @GetMapping("/{codigo}/roles")
    public ApiResponse<List<SistemaRolDto>> roles(@PathVariable String codigo) {
        var sistema = sistemaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new NegocioException("Sistema no encontrado"));
        List<SistemaRolDto> roles = sistemaRolRepository
                .findBySistemaIdAndActivoOrderByOrdenAscCodigoRolAsc(sistema.getId(), 1)
                .stream()
                .map(this::toDto)
                .toList();
        return new ApiResponse<>("OK", "Roles por sistema", roles);
    }

    private SistemaRolDto toDto(SistemaRol rol) {
        return new SistemaRolDto(
                rol.getCodigoRol(),
                rol.getNombre(),
                rol.getDescripcion(),
                rol.getOrden());
    }
}
