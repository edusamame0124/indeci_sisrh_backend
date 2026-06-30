package com.indeci.rrhh.controller;

import com.indeci.rrhh.dto.LiquidacionQuintaDTO;
import com.indeci.rrhh.service.QuintaCategoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rrhh/quinta-categoria")
@RequiredArgsConstructor
public class QuintaCategoriaController {

    private final QuintaCategoriaService quintaCategoriaService;

    @GetMapping("/liquidacion")
    public ResponseEntity<LiquidacionQuintaDTO> generarLiquidacion(
            @RequestParam("empleadoId") Long empleadoId,
            @RequestParam("anioFiscal") Integer anioFiscal,
            @RequestParam("mesFiscal") Integer mesFiscal) {
        
        LiquidacionQuintaDTO liquidacion = quintaCategoriaService.generarLiquidacion(empleadoId, anioFiscal, mesFiscal);
        return ResponseEntity.ok(liquidacion);
    }
}
