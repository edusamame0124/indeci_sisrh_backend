package com.indeci.rrhh.controller;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.CalculoRentaRequestDto;
import com.indeci.rrhh.dto.CalculoRentaResponseDto;
import com.indeci.rrhh.service.CuartaCategoriaService;
import com.indeci.rrhh.service.QuintaCategoriaService;
import com.indeci.rrhh.service.support.RegimenAplicableHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rrhh/impuesto-renta")
@RequiredArgsConstructor
public class ImpuestoRentaController {

    private final CuartaCategoriaService cuartaCategoriaService;
    private final QuintaCategoriaService quintaCategoriaService;

    @PostMapping("/calcular")
    public ResponseEntity<CalculoRentaResponseDto> calcularImpuestoRenta(
            @RequestBody CalculoRentaRequestDto request) {
        
        if (request.getRegimenLaboralCodigo() == null) {
            throw new NegocioException("El régimen laboral es obligatorio para determinar el tipo de renta");
        }

        String regimenNormalizado = RegimenAplicableHelper.normalizar(request.getRegimenLaboralCodigo());

        CalculoRentaResponseDto response;
        if ("1057".equals(regimenNormalizado)) {
            // Régimen CAS -> Renta de 4ta Categoría
            response = cuartaCategoriaService.calcular(request);
        } else if ("276".equals(regimenNormalizado) || "30057".equals(regimenNormalizado)) {
            // Régimen SERVIR o 276 -> Renta de 5ta Categoría
            response = quintaCategoriaService.calcular(request);
        } else {
            throw new NegocioException("Régimen laboral no soportado para el cálculo de retención: " + request.getRegimenLaboralCodigo());
        }

        return ResponseEntity.ok(response);
    }
}
