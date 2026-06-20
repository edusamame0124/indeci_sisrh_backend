package com.indeci.rrhh.controller;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EmpMetaAnualDto;
import com.indeci.rrhh.dto.EmpMetaAnualResponse;
import com.indeci.rrhh.dto.EmpMetaTrazabilidadPageDto;
import com.indeci.rrhh.dto.MetaPptoResumenDto;
import com.indeci.rrhh.service.EmpMetaAnualService;
import com.indeci.security.auth.SisrhSecurityExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rrhh/meta-ppto/asignaciones")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.PLA_READ)
public class EmpMetaAnualController {

    private final EmpMetaAnualService service;

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<EmpMetaAnualResponse>> listarPorEmpleado(@PathVariable Long empleadoId) {
        return new ApiResponse<>("OK", "Asignaciones del empleado", service.listarPorEmpleado(empleadoId));
    }

    @GetMapping("/anio/{anioFiscal}")
    public ApiResponse<List<EmpMetaAnualResponse>> listarPorAnio(
            @PathVariable Integer anioFiscal,
            @RequestParam(required = false) String estado) {
        return new ApiResponse<>("OK", "Asignaciones del año", service.listarPorAnioYEstado(anioFiscal, estado));
    }

    @GetMapping("/resumen/{anioFiscal}")
    public ApiResponse<MetaPptoResumenDto> resumen(@PathVariable Integer anioFiscal) {
        return new ApiResponse<>("OK", "Resumen de metas", service.resumen(anioFiscal));
    }

    @GetMapping("/trazabilidad")
    public ApiResponse<EmpMetaTrazabilidadPageDto> trazabilidad(
            @RequestParam Integer anioFiscal,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String centroCosto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamanio) {
        return new ApiResponse<>("OK", "Trazabilidad de metas",
                service.trazabilidad(anioFiscal, estado, busqueda, centroCosto, pagina, tamanio));
    }

    @GetMapping("/{id}")
    public ApiResponse<EmpMetaAnualResponse> obtener(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Asignación", service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<EmpMetaAnualResponse> asignar(
            @RequestBody EmpMetaAnualDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Asignación creada", service.asignar(dto, user.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<EmpMetaAnualResponse> editar(
            @PathVariable Long id,
            @RequestBody EmpMetaAnualDto dto,
            @AuthenticationPrincipal UserDetails user) {
        return new ApiResponse<>("OK", "Asignación actualizada", service.editar(id, dto, user.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.PLA_WRITE)
    public ApiResponse<Void> anular(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {
        service.anular(id, body.getOrDefault("motivo", "Anulado por usuario"), user.getUsername());
        return new ApiResponse<>("OK", "Asignación anulada", null);
    }
}
