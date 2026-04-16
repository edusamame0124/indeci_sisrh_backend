package com.indeci.auth.controller;

import com.indeci.auth.dto.SesionDto;
import com.indeci.auth.service.SesionService;
import com.indeci.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SesionController {

    private final SesionService sesionService;

    // 🔹 LISTAR
    @GetMapping("/sesiones")
    public List<SesionDto> listar(@RequestHeader("Authorization") String authHeader) {
        return sesionService.listarSesiones(authHeader);
    }

 // 🔹 CERRAR UNA
    @PostMapping("/logout/{id}")
    public ApiResponse<Void> cerrar(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        sesionService.cerrarSesion(id, authHeader);
        return new ApiResponse<>("OK", "Sesión cerrada correctamente", null);
    }
 // 🔹 CERRAR TODAS
    @PostMapping("/logout-all")
    public ApiResponse<Void> cerrarTodas(@RequestHeader("Authorization") String authHeader) {
        sesionService.cerrarTodas(authHeader);
        return new ApiResponse<>("OK", "Todas las sesiones fueron cerradas", null);
    }
 // 🔹 CERRAR OTRAS
    @PostMapping("/logout-others")
    public ApiResponse<Void> cerrarOtras(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody String refreshToken
    ) {
        sesionService.cerrarOtras(authHeader, refreshToken);
        return new ApiResponse<>("OK", "Otras sesiones cerradas correctamente", null);
    }
}