package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ConceptoPlanillaDto;
import com.indeci.rrhh.dto.ConceptoPlanillaResponseDto;
import com.indeci.rrhh.service.ConceptoPlanillaService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/concepto-planilla")
@RequiredArgsConstructor
public class ConceptoPlanillaController {

    private final ConceptoPlanillaService service;

    @PostMapping
    public ApiResponse<Void> guardar(
            @RequestBody ConceptoPlanillaDto dto) {

        service.guardar(dto);

        return new ApiResponse<>(
                "OK",
                "Concepto registrado",
                null);
    }

    @GetMapping
    public ApiResponse<List<ConceptoPlanillaResponseDto>> listar() {

        return new ApiResponse<>(
                "OK",
                "Lista conceptos",
                service.listar());
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> actualizar(
            @PathVariable Long id,
            @RequestBody ConceptoPlanillaDto dto) {

        service.actualizar(id, dto);

        return new ApiResponse<>(
                "OK",
                "Concepto actualizado",
                null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return new ApiResponse<>(
                "OK",
                "Concepto eliminado",
                null);
    }
}