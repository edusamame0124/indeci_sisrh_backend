package com.indeci.rrhh.controller;

import com.indeci.rrhh.dto.EmpleadoOtrosIngresosDto;
import com.indeci.rrhh.service.OtrosIngresosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rrhh/quinta-categoria/otros-ingresos")
@RequiredArgsConstructor
public class OtrosIngresosController {

    private final OtrosIngresosService otrosIngresosService;

    @GetMapping
    public ResponseEntity<EmpleadoOtrosIngresosDto> obtenerOtrosIngresos(
            @RequestParam("empleadoId") Long empleadoId,
            @RequestParam("anioFiscal") Integer anioFiscal) {
        EmpleadoOtrosIngresosDto dto = otrosIngresosService.obtenerPorEmpleadoYAno(empleadoId, anioFiscal);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<EmpleadoOtrosIngresosDto> guardarOtrosIngresos(
            @Valid @RequestBody EmpleadoOtrosIngresosDto dto) {
        EmpleadoOtrosIngresosDto guardado = otrosIngresosService.guardar(dto);
        return ResponseEntity.ok(guardado);
    }
}
