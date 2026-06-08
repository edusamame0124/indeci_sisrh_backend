package com.indeci.rrhh.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.indeci.common.dto.ApiResponse;
import com.indeci.rrhh.dto.EventoPeriodoDto;
import com.indeci.rrhh.dto.EventoPeriodoPageDto;
import com.indeci.rrhh.dto.EventoPeriodoResponseDto;
import com.indeci.rrhh.dto.MaternidadPreviewDto;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.TipoEventoRepository;
import com.indeci.rrhh.service.EventoPeriodoService;
import com.indeci.security.auth.SisrhSecurityExpressions;

import lombok.RequiredArgsConstructor;

/**
 * F2.5 — Endpoint REST de eventos del período del empleado.
 *
 * <p>Rutas:</p>
 * <ul>
 *   <li>{@code GET    /api/rrhh/evento-periodo/tipos}                  — catálogo de tipos</li>
 *   <li>{@code GET    /api/rrhh/evento-periodo}                        — bandeja paginada</li>
 *   <li>{@code GET    /api/rrhh/evento-periodo/{id}}                   — obtener uno</li>
 *   <li>{@code GET    /api/rrhh/evento-periodo/empleado/{empleadoId}}  — listar por empleado</li>
 *   <li>{@code POST   /api/rrhh/evento-periodo}                        — crear</li>
 *   <li>{@code PUT    /api/rrhh/evento-periodo/{id}}                   — actualizar</li>
 *   <li>{@code PUT    /api/rrhh/evento-periodo/{id}/estado/{estado}}   — cambiar estado</li>
 *   <li>{@code DELETE /api/rrhh/evento-periodo/{id}}                   — eliminar (baja lógica)</li>
 * </ul>
 *
 * <p>Permisos: lectura accesible a roles {@code EMP_READ}, escritura/cambio
 * de estado/eliminación requiere {@code EMP_WRITE} (mismo patrón que
 * {@code SuspensionController}).</p>
 */
@RestController
@RequestMapping("/api/rrhh/evento-periodo")
@RequiredArgsConstructor
@PreAuthorize(SisrhSecurityExpressions.EMP_READ)
public class EventoPeriodoController {

    private final EventoPeriodoService service;
    private final TipoEventoRepository tipoRepository;

    @GetMapping("/tipos")
    public ApiResponse<List<TipoEvento>> catalogo() {
        return new ApiResponse<>(
                "OK",
                "Catálogo de tipos de evento",
                tipoRepository.findByActivoOrderByOrdenVisualAsc(1));
    }

    @GetMapping("/empleado/{empleadoId}")
    public ApiResponse<List<EventoPeriodoResponseDto>> listarPorEmpleado(
            @PathVariable Long empleadoId) {
        return new ApiResponse<>(
                "OK",
                "Eventos del período del empleado",
                service.listarPorEmpleado(empleadoId));
    }

    @GetMapping
    public ApiResponse<EventoPeriodoPageDto> listar(
            @RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false) Long tipoEventoId,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(
                "OK",
                "Eventos del período registrados",
                service.listarPaginado(empleadoId, tipoEventoId, estado, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventoPeriodoResponseDto> obtener(@PathVariable Long id) {
        return new ApiResponse<>("OK", "Evento", service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<EventoPeriodoResponseDto> crear(
            @RequestBody EventoPeriodoDto dto) {
        return new ApiResponse<>(
                "OK", "Evento registrado", service.crear(dto));
    }

    @PostMapping("/preview-maternidad")
    @PreAuthorize(SisrhSecurityExpressions.EMP_READ)
    public ApiResponse<MaternidadPreviewDto> previewMaternidad(
            @RequestBody EventoPeriodoDto dto) {
        return new ApiResponse<>(
                "OK", "Preview de impacto maternidad", service.previewMaternidad(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<EventoPeriodoResponseDto> actualizar(
            @PathVariable Long id,
            @RequestBody EventoPeriodoDto dto) {
        return new ApiResponse<>(
                "OK", "Evento actualizado", service.actualizar(id, dto));
    }

    @PutMapping("/{id}/estado/{estado}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<EventoPeriodoResponseDto> cambiarEstado(
            @PathVariable Long id,
            @PathVariable String estado) {
        return new ApiResponse<>(
                "OK", "Estado actualizado", service.cambiarEstado(id, estado));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SisrhSecurityExpressions.EMP_WRITE)
    public ApiResponse<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ApiResponse<>("OK", "Evento eliminado", null);
    }
}
