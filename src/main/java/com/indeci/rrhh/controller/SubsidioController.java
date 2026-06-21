package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.subsidio.SubsidioBaseHistoricaResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCasoDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCasoPageDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCasoResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCittDto;
import com.indeci.rrhh.dto.subsidio.SubsidioCittResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioLiquidacionExplicacionDto;
import com.indeci.rrhh.dto.subsidio.SubsidioLiquidacionResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioRevertirDto;
import com.indeci.rrhh.dto.subsidio.SubsidioTimelineEventoDto;
import com.indeci.rrhh.dto.subsidio.SubsidioTramoResponseDto;
import com.indeci.rrhh.dto.subsidio.SubsidioTramoUpdateDto;
import com.indeci.rrhh.dto.subsidio.SubsidioValidacionDto;
import com.indeci.rrhh.service.subsidio.SubsidioCasoService;
import com.indeci.rrhh.service.subsidio.SubsidioLiquidacionService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * P0-F2 — API REST del módulo Subsidios ({@code /api/rrhh/subsidios}).
 */
@RestController
@RequestMapping("/api/rrhh/subsidios")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.SUB_READ)
public class SubsidioController {

    private final SubsidioCasoService casoService;
    private final SubsidioLiquidacionService liquidacionService;

    @GetMapping("/casos")
    public ApiResponse<SubsidioCasoPageDto> listarCasos(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false) String dni,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>("OK", "Bandeja de casos de subsidio",
                casoService.listar(periodo, tipo, estado, empleadoId, dni, page, size));
    }

    @GetMapping("/casos/{id}")
    public ApiResponse<SubsidioCasoResponseDto> obtenerCaso(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Detalle del caso", casoService.obtener(id));
    }

    @PostMapping("/casos")
    @PreAuthorize(SisrhSecurityExpressions.SUB_WRITE)
    public ApiResponse<SubsidioCasoResponseDto> crearCaso(@RequestBody SubsidioCasoDto dto) {
        return new ApiResponse<>("OK", "Caso registrado", casoService.crear(dto));
    }

    @PutMapping("/casos/{id}")
    @PreAuthorize(SisrhSecurityExpressions.SUB_WRITE)
    public ApiResponse<SubsidioCasoResponseDto> actualizarCaso(
            @PathVariable Long id, @RequestBody SubsidioCasoDto dto) {
        return new ApiResponse<>("OK", "Caso actualizado", casoService.actualizar(id, dto));
    }

    @PutMapping("/casos/{id}/estado/{estado}")
    @PreAuthorize(SisrhSecurityExpressions.SUB_VALIDATE)
    public ApiResponse<SubsidioCasoResponseDto> cambiarEstado(
            @PathVariable Long id, @PathVariable String estado) {
        return new ApiResponse<>("OK", "Estado actualizado", casoService.cambiarEstado(id, estado));
    }

    @GetMapping("/casos/{id}/timeline")
    public ApiResponse<List<SubsidioTimelineEventoDto>> timeline(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Línea de tiempo del caso", casoService.timeline(id));
    }

    @GetMapping("/casos/{id}/validaciones")
    public ApiResponse<List<SubsidioValidacionDto>> validaciones(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Validaciones del caso", casoService.validaciones(id));
    }

    @PostMapping("/casos/{id}/citt")
    @PreAuthorize(SisrhSecurityExpressions.SUB_WRITE)
    public ApiResponse<SubsidioCittResponseDto> registrarCitt(
            @PathVariable Long id, @RequestBody SubsidioCittDto dto) {
        return new ApiResponse<>("OK", "CITT registrado", casoService.registrarCitt(id, dto));
    }

    @PutMapping("/citt/{cittId}")
    @PreAuthorize(SisrhSecurityExpressions.SUB_WRITE)
    public ApiResponse<SubsidioCittResponseDto> actualizarCitt(
            @PathVariable Long cittId, @RequestBody SubsidioCittDto dto) {
        return new ApiResponse<>("OK", "CITT actualizado", casoService.actualizarCitt(cittId, dto));
    }

    @GetMapping("/casos/{id}/citt")
    public ApiResponse<List<SubsidioCittResponseDto>> listarCitt(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Certificados CITT del caso", casoService.listarCitt(id));
    }

    @PostMapping("/casos/{id}/tramos/generar")
    @PreAuthorize(SisrhSecurityExpressions.SUB_WRITE)
    public ApiResponse<List<SubsidioTramoResponseDto>> generarTramos(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Tramos mensuales generados", casoService.generarTramos(id));
    }

    @PutMapping("/tramos/{tramoId}")
    @PreAuthorize(SisrhSecurityExpressions.SUB_WRITE)
    public ApiResponse<SubsidioTramoResponseDto> actualizarTramo(
            @PathVariable Long tramoId, @RequestBody SubsidioTramoUpdateDto dto) {
        return new ApiResponse<>("OK", "Tramo actualizado",
                casoService.actualizarTramo(tramoId, dto.diasSubsidio(), dto.diasLaborados()));
    }

    @PostMapping("/casos/{id}/base-historica/calcular")
    @PreAuthorize(SisrhSecurityExpressions.SUB_CALCULATE)
    public ApiResponse<SubsidioBaseHistoricaResponseDto> calcularBase(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Base histórica calculada", casoService.calcularBaseHistorica(id));
    }

    @GetMapping("/casos/{id}/base-historica")
    public ApiResponse<SubsidioBaseHistoricaResponseDto> obtenerBase(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Base histórica vigente", casoService.obtenerBaseHistorica(id));
    }

    @PostMapping("/tramos/{tramoId}/liquidaciones/calcular")
    @PreAuthorize(SisrhSecurityExpressions.SUB_CALCULATE)
    public ApiResponse<SubsidioLiquidacionResponseDto> calcularLiquidacion(
            @PathVariable Long tramoId) {
        return new ApiResponse<>("OK", "Liquidación calculada", casoService.calcularLiquidacion(tramoId));
    }

    @GetMapping("/tramos/{tramoId}/liquidaciones")
    public ApiResponse<List<SubsidioLiquidacionResponseDto>> historialLiquidaciones(
            @PathVariable Long tramoId) {
        return new ApiResponse<>("OK", "Historial de liquidaciones",
                casoService.historialLiquidaciones(tramoId));
    }

    @GetMapping("/liquidaciones/{id}/explicacion")
    public ApiResponse<SubsidioLiquidacionExplicacionDto> explicacion(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Explicación del cálculo", liquidacionService.explicacion(id));
    }

    @PostMapping("/liquidaciones/{id}/aplicar-planilla")
    @PreAuthorize(SisrhSecurityExpressions.SUB_APPLY_PLANILLA)
    public ApiResponse<SubsidioLiquidacionResponseDto> aplicarPlanilla(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Liquidación aplicada a planilla", casoService.aplicarPlanilla(id));
    }

    @PostMapping("/liquidaciones/{id}/revertir")
    @PreAuthorize(SisrhSecurityExpressions.SUB_ADJUST)
    public ApiResponse<SubsidioLiquidacionResponseDto> revertir(
            @PathVariable Long id, @RequestBody SubsidioRevertirDto dto) {
        return new ApiResponse<>("OK", "Liquidación revertida",
                casoService.revertirPlanilla(id, dto.motivo()));
    }
}
