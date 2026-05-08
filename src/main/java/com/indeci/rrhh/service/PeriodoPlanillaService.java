package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PeriodoPlanillaDto;
import com.indeci.rrhh.dto.PeriodoPlanillaResponseDto;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PeriodoPlanillaService {

    private final PeriodoPlanillaRepository repository;
    private final AuditoriaContext auditoriaContext;

    // ============================
    // CREAR
    // ============================

    @Auditable(accion = "CREAR_PERIODO_PLANILLA")
    public void guardar(PeriodoPlanillaDto dto) {

        repository.findByPeriodoAndActivo(
                dto.getPeriodo(),
                1)
                .ifPresent(p -> {
                    throw new NegocioException(
                            "Periodo ya existe");
                });

        PeriodoPlanilla entity =
                new PeriodoPlanilla();

        entity.setPeriodo(dto.getPeriodo());
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());

        entity.setEstado("ABIERTO");

        entity.setObservacion(dto.getObservacion());

        entity.setActivo(1);

        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);

        auditoriaContext.setDetalle(
                "Periodo creado: "
                        + dto.getPeriodo());
    }

    // ============================
    // LISTAR
    // ============================

    public List<PeriodoPlanillaResponseDto> listar() {

        return repository.findByActivo(1)
                .stream()
                .map(e -> {

                    PeriodoPlanillaResponseDto dto =
                            new PeriodoPlanillaResponseDto();

                    dto.setId(e.getId());
                    dto.setPeriodo(e.getPeriodo());
                    dto.setFechaInicio(e.getFechaInicio());
                    dto.setFechaFin(e.getFechaFin());
                    dto.setEstado(e.getEstado());
                    dto.setObservacion(e.getObservacion());
                    dto.setFechaCierre(e.getFechaCierre());
                    dto.setActivo(e.getActivo());

                    return dto;

                }).toList();
    }

    // ============================
    // CERRAR PERIODO
    // ============================

    @Auditable(accion = "CERRAR_PERIODO_PLANILLA")
    public void cerrar(Long id) {

        PeriodoPlanilla entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Periodo no encontrado"));

        if ("CERRADO".equals(entity.getEstado())) {
            throw new NegocioException(
                    "Periodo ya cerrado");
        }

        entity.setEstado("CERRADO");

        entity.setFechaCierre(
                LocalDateTime.now());

        repository.save(entity);

        auditoriaContext.setDetalle(
                "Periodo cerrado ID: "
                        + id);
    }

    // ============================
    // REABRIR
    // ============================

    @Auditable(accion = "REABRIR_PERIODO_PLANILLA")
    public void reabrir(Long id) {

        PeriodoPlanilla entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Periodo no encontrado"));

        entity.setEstado("ABIERTO");

        entity.setFechaCierre(null);

        repository.save(entity);

        auditoriaContext.setDetalle(
                "Periodo reabierto ID: "
                        + id);
    }

    // ============================
    // ELIMINAR
    // ============================

    @Auditable(accion = "ELIMINAR_PERIODO_PLANILLA")
    public void eliminar(Long id) {

        PeriodoPlanilla entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Periodo no encontrado"));

        entity.setActivo(0);

        repository.save(entity);

        auditoriaContext.setDetalle(
                "Periodo eliminado ID: "
                        + id);
    }
}