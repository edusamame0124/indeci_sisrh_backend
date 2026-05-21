package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ConceptoPlanillaDto;
import com.indeci.rrhh.dto.ConceptoPlanillaResponseDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConceptoPlanillaService {

    private final ConceptoPlanillaRepository repository;
    private final AuditoriaContext auditoriaContext;

    // ============================
    // CREAR
    // ============================

    @Auditable(accion = "CREAR_CONCEPTO_PLANILLA")
    public void guardar(ConceptoPlanillaDto dto) {

        ConceptoPlanilla entity = new ConceptoPlanilla();

        entity.setCodigo(dto.getCodigo());
        entity.setNombre(dto.getNombre());
        entity.setTipo(dto.getTipo());
        entity.setNaturaleza(dto.getNaturaleza());

        entity.setActivo(1);
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);

        auditoriaContext.setDetalle(
                "Concepto planilla creado: " + dto.getNombre());
    }

    // ============================
    // LISTAR
    // ============================

    public List<ConceptoPlanillaResponseDto> listar() {

        return repository.findByActivo(1)
                .stream()
                .map(e -> {

                    ConceptoPlanillaResponseDto dto =
                            new ConceptoPlanillaResponseDto();

                    dto.setId(e.getId());
                    dto.setCodigo(e.getCodigo());
                    dto.setNombre(e.getNombre());
                    dto.setTipo(e.getTipo());
                    dto.setNaturaleza(e.getNaturaleza());
                    dto.setActivo(e.getActivo());

                    // Spec 013 / C1 — campos MEF para el dropdown del modal.
                    dto.setCodigoMef(e.getCodigoMef());
                    dto.setCodigoSisper(e.getCodigoSisper());
                    dto.setTipoConcepto(e.getTipoConcepto());

                    return dto;

                }).toList();
    }

    // ============================
    // ACTUALIZAR
    // ============================

    @Auditable(accion = "ACTUALIZAR_CONCEPTO_PLANILLA")
    public void actualizar(Long id, ConceptoPlanillaDto dto) {

        ConceptoPlanilla entity = repository.findById(id)
                .orElseThrow(() ->
                        new NegocioException("Concepto no encontrado"));

        entity.setCodigo(dto.getCodigo());
        entity.setNombre(dto.getNombre());
        entity.setTipo(dto.getTipo());
        entity.setNaturaleza(dto.getNaturaleza());

        repository.save(entity);

        auditoriaContext.setDetalle(
                "Concepto planilla actualizado ID: " + id);
    }

    // ============================
    // ELIMINAR
    // ============================

    @Auditable(accion = "ELIMINAR_CONCEPTO_PLANILLA")
    public void eliminar(Long id) {

        ConceptoPlanilla entity = repository.findById(id)
                .orElseThrow(() ->
                        new NegocioException("Concepto no encontrado"));

        entity.setActivo(0);

        repository.save(entity);

        auditoriaContext.setDetalle(
                "Concepto planilla eliminado ID: " + id);
    }
}