package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.ReintegroMontoDto;
import com.indeci.rrhh.service.ReintegroMontoService;
import com.indeci.security.auth.SisrhSecurityExpressions;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rrhh/reintegros")
@RequiredArgsConstructor
public class ReintegroMontoController {

    private final ReintegroMontoService reintegroMontoService;

    @PostMapping("/registrar")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> registrarReintegro(@Valid @RequestBody ReintegroMontoDto request) {
        reintegroMontoService.registrarReintegro(request);
        return new ApiResponse<>("OK", "Reintegro registrado con éxito", null);
    }
}
