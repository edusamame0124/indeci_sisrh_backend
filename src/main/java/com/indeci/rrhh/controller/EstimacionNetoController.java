package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EstimacionNetoDto;
import com.indeci.rrhh.dto.EstimacionNetoRequestDto;
import com.indeci.rrhh.service.EstimacionNetoService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

/**
 * Spec 013 / C1 — Preview de neto del modal "Asignar Descuento / Ajuste Manual".
 *
 * <p>Controller NUEVO. El endpoint es de SOLO LECTURA: estima el neto del
 * empleado si se le agregara el descuento propuesto y NO graba nada en BD.
 * El acceso es el mismo que el de asignar conceptos (ROL_RRHH) — sin restricción
 * adicional, igual que {@code EmpleadoConceptoController}.
 */
@RestController
@RequestMapping("/api/rrhh/empleados")
@RequiredArgsConstructor
public class EstimacionNetoController {

    private final EstimacionNetoService service;

    @PostMapping("/{id}/estimar-neto")
    public ApiResponse<EstimacionNetoDto> estimarNeto(
            @PathVariable Long id,
            @RequestBody EstimacionNetoRequestDto request) {

        EstimacionNetoDto dto = service.estimarNeto(
                id, request.getConceptoId(), request.getMonto());

        return new ApiResponse<>("OK", "Estimación de neto", dto);
    }
}
