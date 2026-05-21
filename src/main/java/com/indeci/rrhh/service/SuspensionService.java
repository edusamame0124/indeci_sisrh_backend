package com.indeci.rrhh.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SuspensionDto;
import com.indeci.rrhh.dto.SuspensionResponseDto;
import com.indeci.rrhh.entity.CatSuspensionSunat;
import com.indeci.rrhh.entity.Suspension;
import com.indeci.rrhh.repository.CatSuspensionSunatRepository;
import com.indeci.rrhh.repository.SuspensionRepository;

import lombok.RequiredArgsConstructor;

/**
 * B3 / M09 — Gestión de suspensiones/licencias del empleado (fuente del .snl/.jor).
 * CRUD con baja lógica (ESTADO ACTIVO|ANULADO).
 *
 * <p>Validaciones: el código debe existir en el catálogo SUNAT; fechas coherentes;
 * días afectos no negativos; sin solape con otra suspensión activa del empleado.
 */
@Service
@RequiredArgsConstructor
public class SuspensionService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_ANULADO = "ANULADO";

    private final SuspensionRepository repository;
    private final CatSuspensionSunatRepository catalogoRepository;
    private final AuditoriaContext auditoriaContext;

    // ============================ CATÁLOGO ============================
    public List<CatSuspensionSunat> catalogo() {
        return catalogoRepository.findAll();
    }

    // ============================ LISTAR ============================
    public List<SuspensionResponseDto> listar(Long empleadoId) {
        return repository.findByEmpleadoIdAndEstadoOrderByFechaInicio(empleadoId, ESTADO_ACTIVO)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================ CREAR ============================
    @Auditable(accion = "CREAR_SUSPENSION")
    public SuspensionResponseDto crear(SuspensionDto dto) {
        CatSuspensionSunat cat = validar(dto, null);

        Suspension entity = new Suspension();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setCodSuspension(cat.getCodSuspension());
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setDiasAfectos(dto.getDiasAfectos());
        entity.setNroCmp(dto.getNroCmp());
        entity.setNroResolucion(dto.getNroResolucion());
        entity.setObservacion(dto.getObservacion());
        entity.setEstado(ESTADO_ACTIVO);
        entity.setCreatedAt(LocalDateTime.now());

        Suspension guardada = repository.save(entity);
        auditoriaContext.setDetalle("Suspensión creada — empleado " + dto.getEmpleadoId()
                + ", código " + cat.getCodSuspension());
        return toResponse(guardada);
    }

    // ============================ ACTUALIZAR ============================
    @Auditable(accion = "ACTUALIZAR_SUSPENSION")
    public SuspensionResponseDto actualizar(Long id, SuspensionDto dto) {
        Suspension entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Suspensión no encontrada"));

        CatSuspensionSunat cat = validar(dto, id);

        entity.setCodSuspension(cat.getCodSuspension());
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setDiasAfectos(dto.getDiasAfectos());
        entity.setNroCmp(dto.getNroCmp());
        entity.setNroResolucion(dto.getNroResolucion());
        entity.setObservacion(dto.getObservacion());

        Suspension guardada = repository.save(entity);
        auditoriaContext.setDetalle("Suspensión actualizada ID: " + id);
        return toResponse(guardada);
    }

    // ============================ ELIMINAR (LÓGICO) ============================
    @Auditable(accion = "ELIMINAR_SUSPENSION")
    public void eliminar(Long id) {
        Suspension entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Suspensión no encontrada"));
        entity.setEstado(ESTADO_ANULADO);
        repository.save(entity);
        auditoriaContext.setDetalle("Suspensión anulada ID: " + id);
    }

    // ============================ HELPERS ============================

    /** Valida el DTO y devuelve el catálogo. {@code idActual} excluye la propia fila al editar. */
    private CatSuspensionSunat validar(SuspensionDto dto, Long idActual) {
        if (dto.getEmpleadoId() == null) {
            throw new NegocioException("La suspensión requiere empleadoId");
        }
        if (dto.getCodSuspension() == null || dto.getCodSuspension().isBlank()) {
            throw new NegocioException("La suspensión requiere código SUNAT");
        }
        CatSuspensionSunat cat = catalogoRepository.findById(dto.getCodSuspension())
                .orElseThrow(() -> new NegocioException(
                        "Código de suspensión no existe en el catálogo SUNAT: " + dto.getCodSuspension()));

        if (dto.getFechaInicio() == null || dto.getFechaFin() == null) {
            throw new NegocioException("La suspensión requiere fecha de inicio y fin");
        }
        if (dto.getFechaFin().isBefore(dto.getFechaInicio())) {
            throw new NegocioException("La fecha fin no puede ser anterior a la fecha inicio");
        }
        if (dto.getDiasAfectos() == null || dto.getDiasAfectos() < 0) {
            throw new NegocioException("Los días afectos deben ser un número no negativo");
        }

        validarSinSolape(dto, idActual);
        return cat;
    }

    /** Una nueva suspensión no puede solapar con otra ACTIVA del mismo empleado. */
    private void validarSinSolape(SuspensionDto dto, Long idActual) {
        List<Suspension> activas = repository
                .findByEmpleadoIdAndEstadoOrderByFechaInicio(dto.getEmpleadoId(), ESTADO_ACTIVO);
        for (Suspension s : activas) {
            if (idActual != null && idActual.equals(s.getId())) {
                continue; // no choca consigo misma al editar
            }
            boolean solapa = !s.getFechaInicio().isAfter(dto.getFechaFin())
                    && !s.getFechaFin().isBefore(dto.getFechaInicio());
            if (solapa) {
                throw new NegocioException(
                        "La suspensión solapa con otra activa del empleado (ID " + s.getId() + ")");
            }
        }
    }

    private SuspensionResponseDto toResponse(Suspension e) {
        SuspensionResponseDto dto = new SuspensionResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setCodSuspension(e.getCodSuspension());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFin(e.getFechaFin());
        dto.setDiasAfectos(e.getDiasAfectos());
        dto.setNroCmp(e.getNroCmp());
        dto.setNroResolucion(e.getNroResolucion());
        dto.setObservacion(e.getObservacion());
        dto.setEstado(e.getEstado());

        catalogoRepository.findById(e.getCodSuspension()).ifPresent(c -> {
            dto.setDescripcionSuspension(c.getDescripcion());
            dto.setTipoPlame(c.getTipoPlame());
        });
        return dto;
    }
}
