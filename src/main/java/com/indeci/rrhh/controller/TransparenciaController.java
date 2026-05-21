package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.TransparenciaPeriodoDto;
import com.indeci.rrhh.dto.TransparenciaRemuneracionDto;
import com.indeci.rrhh.service.TransparenciaService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Spec 011 / B4 — M10 Transparencia (Ley 27806).
 * Endpoints PÚBLICOS (sin autenticación — ver SecurityConfig): exponen las
 * remuneraciones del personal de los períodos ya finalizados.
 */
@RestController
@RequestMapping("/api/transparencia")
@RequiredArgsConstructor
public class TransparenciaController {

    private final TransparenciaService service;

    /** Períodos publicados (APROBADO / CERRADO). */
    @GetMapping("/periodos")
    public ApiResponse<List<TransparenciaPeriodoDto>> periodos() {
        return new ApiResponse<>("OK", "Periodos publicados",
                service.periodosPublicados());
    }

    /** Remuneraciones públicas de un período (Ley 27806). */
    @GetMapping("/remuneraciones/{periodo}")
    public ApiResponse<List<TransparenciaRemuneracionDto>> remuneraciones(
            @PathVariable String periodo) {
        return new ApiResponse<>("OK", "Remuneraciones del periodo",
                service.remuneraciones(periodo));
    }
}
