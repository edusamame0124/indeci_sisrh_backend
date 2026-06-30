package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.CandidatoAdicionalDto;
import com.indeci.rrhh.dto.GenerarPlanillaCabeceraDto;
import com.indeci.rrhh.dto.PlanillaLoteDashboardDto;
import com.indeci.rrhh.service.PlanillaLoteService;
import com.indeci.rrhh.repository.PlanillaLoteRepository;
import com.indeci.security.auth.SisrhSecurityExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rrhh/planillas-lote")
@RequiredArgsConstructor
public class PlanillaLoteController {

    private final PlanillaLoteService planillaLoteService;
    private final PlanillaLoteRepository planillaLoteRepository;

    @GetMapping("/lotes")
    @PreAuthorize(SisrhSecurityExpressions.PLA_READ)
    public ApiResponse<List<PlanillaLoteDashboardDto>> obtenerLotesDashboard(
            @RequestParam("periodo") String periodo,
            @RequestParam(value = "regimen", required = false) String regimen) {

        List<PlanillaLoteDashboardDto> lotes = planillaLoteRepository.findLotesDashboard(periodo, regimen);
        return new ApiResponse<>("OK", "Lotes de planilla obtenidos", lotes);
    }

    @GetMapping("/candidatos-adicional")
    @PreAuthorize(SisrhSecurityExpressions.PLA_READ)
    public ApiResponse<List<CandidatoAdicionalDto>> obtenerCandidatosAdicionales(
            @RequestParam("periodo") String periodo) {

        List<CandidatoAdicionalDto> candidatos = planillaLoteService.obtenerCandidatosAdicionales(periodo);
        return new ApiResponse<>("OK", "Candidatos para planilla adicional", candidatos);
    }

    @PostMapping("/generar-adicional")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> generarLoteAdicional(
            @RequestBody GenerarPlanillaCabeceraDto request) {

        planillaLoteService.generarLoteAdicional(request);
        return new ApiResponse<>("OK", "Lote adicional generado con éxito", null);
    }
}
