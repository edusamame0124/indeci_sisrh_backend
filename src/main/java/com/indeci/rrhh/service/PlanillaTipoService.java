package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PlanillaTipoDto;
import com.indeci.rrhh.entity.PlanillaTipo;
import com.indeci.rrhh.repository.PlanillaTipoRepository;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
        if (esBlank(dto.getNombre())) {
            throw new NegocioException("El nombre del tipo de planilla es obligatorio.");
        }
        
        String codigoGenerado = dto.getNombre().trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        if (codigoGenerado.length() > 20) {
            codigoGenerado = codigoGenerado.substring(0, 20);
        }
        if (codigoGenerado.endsWith("_")) {
            codigoGenerado = codigoGenerado.substring(0, codigoGenerado.length() - 1);
        }
        
        if (repository.existsById(codigoGenerado)) {
            throw new NegocioException(
                    "Ya existe un tipo de planilla con código autogenerado " + codigoGenerado + ".");
        }

        Integer maxOrden = repository.findMaxOrden();
        Integer ordenNuevo = (maxOrden == null) ? 1 : maxOrden + 1;

        PlanillaTipo e = new PlanillaTipo();
        e.setCodigo(codigoGenerado);
        e.setNombre(dto.getNombre().trim().toUpperCase());
        e.setDescripcion(dto.getDescripcion());
        e.setOrden(ordenNuevo);
        e.setActivo(dto.getActivo() != null ? dto.getActivo() : 1);
        
        e.setCreadoPor(obtenerUsuarioActual());
        e.setCreadoEn(LocalDateTime.now());
        
        repository.save(e);

        auditoriaContext.setDetalle("Tipo de planilla creado: " + codigoGenerado);
        return toDto(e);
    }

    @Auditable(accion = "ACTUALIZAR_PLANILLA_TIPO")
    public PlanillaTipoDto actualizar(String codigo, PlanillaTipoDto dto) {
        PlanillaTipo e = obtener(codigo);
        if (!esBlank(dto.getNombre())) {
            e.setNombre(dto.getNombre().trim().toUpperCase());
        }
        if (dto.getDescripcion() != null) {
            e.setDescripcion(dto.getDescripcion());
        }
        if (dto.getOrden() != null) {
            e.setOrden(dto.getOrden());
        }
        if (dto.getActivo() != null) {
            e.setActivo(dto.getActivo());
        }
        
        e.setModificadoPor(obtenerUsuarioActual());
        e.setModificadoEn(LocalDateTime.now());
        
        repository.save(e);

        auditoriaContext.setDetalle("Tipo de planilla actualizado: " + codigo);
        return toDto(e);
    }

    /** Baja lógica ({@code ACTIVO=0}); preserva el histórico de asociaciones. */
    @Auditable(accion = "ELIMINAR_PLANILLA_TIPO")
    public void eliminar(String codigo) {
        PlanillaTipo e = obtener(codigo);
        e.setActivo(0);
        e.setModificadoPor(obtenerUsuarioActual());
        e.setModificadoEn(LocalDateTime.now());
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
        dto.setDescripcion(e.getDescripcion());
        dto.setOrden(e.getOrden());
        dto.setActivo(e.getActivo());
        return dto;
    }

    private static boolean esBlank(String s) {
        return s == null || s.isBlank();
    }

    private String obtenerUsuarioActual() {
        try {
            String u = SecurityUtil.getUsername();
            return (u != null && !u.isEmpty()) ? u : "SISTEMA";
        } catch (Exception e) {
            return "SISTEMA";
        }
    }
}
