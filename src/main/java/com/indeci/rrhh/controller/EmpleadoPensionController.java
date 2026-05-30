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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EmpleadoPensionDto;
import com.indeci.rrhh.dto.EmpleadoPensionResponseDto;
import com.indeci.rrhh.dto.TasasVigentesPensionDto;
import com.indeci.rrhh.service.EmpleadoPensionService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rrhh/pension")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class EmpleadoPensionController {

    private final EmpleadoPensionService service;

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> guardar(@RequestBody EmpleadoPensionDto dto) {
        service.guardar(dto);
        return new ApiResponse<>("OK", "Pensión registrada", null);
    }

    @GetMapping("/{empleadoId}")
    public ApiResponse<List<EmpleadoPensionResponseDto>> listar(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Pensión del empleado", service.listar(empleadoId));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> actualizar(@PathVariable Long id, @RequestBody EmpleadoPensionDto dto) {
        service.actualizar(id, dto);
        return new ApiResponse<>("OK", "Pensión actualizada", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Pensión desactivada", null);
    }

    @GetMapping("/tasas-vigentes")
    public ApiResponse<TasasVigentesPensionDto> tasasVigentes(
            @RequestParam Long regimenPensionarioId,
            @RequestParam(required = false) Long tipoComisionAfpId,
            @RequestParam(required = false) Integer anio) {
        return new ApiResponse<>("OK", "Tasas vigentes",
                service.tasasVigentes(regimenPensionarioId, tipoComisionAfpId, anio));
    }
}
