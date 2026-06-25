package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PlanillaTipoDto;
import com.indeci.rrhh.entity.PlanillaTipo;
import com.indeci.rrhh.repository.PlanillaTipoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — catálogo administrable de tipos de planilla.
 *
 * <p>Listar (lectura) + crear/actualizar/eliminar lógico (admin). Metadata: el motor
 * NO filtra la generación por tipo de planilla en Fase A.</p>
 */
@Service
@RequiredArgsConstructor
public class PlanillaTipoService {

    private final PlanillaTipoRepository repository;
    private final AuditoriaContext auditoriaContext;

    /** Catálogo activo ordenado por {@code ORDEN}. */
    public List<PlanillaTipoDto> listar() {
        return repository.findByActivoOrderByOrden(1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Auditable(accion = "CREAR_PLANILLA_TIPO")
    public PlanillaTipoDto crear(PlanillaTipoDto dto) {
        if (esBlank(dto.getCodigo())) {
            throw new NegocioException("El código del tipo de planilla es obligatorio.");
        }
        if (esBlank(dto.getNombre())) {
            throw new NegocioException("El nombre del tipo de planilla es obligatorio.");
        }
        if (repository.existsById(dto.getCodigo())) {
            throw new NegocioException(
                    "Ya existe un tipo de planilla con código " + dto.getCodigo() + ".");
        }

        PlanillaTipo e = new PlanillaTipo();
        e.setCodigo(dto.getCodigo());
        e.setNombre(dto.getNombre());
        e.setOrden(dto.getOrden());
        e.setActivo(dto.getActivo() != null ? dto.getActivo() : 1);
        repository.save(e);

        auditoriaContext.setDetalle("Tipo de planilla creado: " + dto.getCodigo());
        return toDto(e);
    }

    @Auditable(accion = "ACTUALIZAR_PLANILLA_TIPO")
    public PlanillaTipoDto actualizar(String codigo, PlanillaTipoDto dto) {
        PlanillaTipo e = obtener(codigo);
        if (!esBlank(dto.getNombre())) {
            e.setNombre(dto.getNombre());
        }
        e.setOrden(dto.getOrden());
        if (dto.getActivo() != null) {
            e.setActivo(dto.getActivo());
        }
        repository.save(e);

        auditoriaContext.setDetalle("Tipo de planilla actualizado: " + codigo);
        return toDto(e);
    }

    /** Baja lógica ({@code ACTIVO=0}); preserva el histórico de asociaciones. */
    @Auditable(accion = "ELIMINAR_PLANILLA_TIPO")
    public void eliminar(String codigo) {
        PlanillaTipo e = obtener(codigo);
        e.setActivo(0);
        repository.save(e);
        auditoriaContext.setDetalle("Tipo de planilla eliminado (lógico): " + codigo);
    }

    private PlanillaTipo obtener(String codigo) {
        return repository.findById(codigo)
                .orElseThrow(() -> new NegocioException(
                        "Tipo de planilla no encontrado: " + codigo));
    }

    private PlanillaTipoDto toDto(PlanillaTipo e) {
        PlanillaTipoDto dto = new PlanillaTipoDto();
        dto.setCodigo(e.getCodigo());
        dto.setNombre(e.getNombre());
        dto.setOrden(e.getOrden());
        dto.setActivo(e.getActivo());
        return dto;
    }

    private static boolean esBlank(String s) {
        return s == null || s.isBlank();
    }
}
