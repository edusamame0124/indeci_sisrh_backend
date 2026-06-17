package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ReconocimientoDto;
import com.indeci.rrhh.dto.ReconocimientoResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Reconocimiento;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;
import com.indeci.rrhh.repository.ReconocimientoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconocimientoService {

    private final ReconocimientoRepository repository;

    private final EmpleadoRepository empleadoRepository;

    private final LegajoDocumentoRepository
            legajoDocumentoRepository;

    @Transactional
    public void registrar(
            ReconocimientoDto dto) {

        Empleado empleado =
                empleadoRepository
                        .findById(dto.getEmpleadoId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Empleado no existe"));

        if(dto.getLegajoDocumentoId() != null) {

            legajoDocumentoRepository
                    .findById(
                            dto.getLegajoDocumentoId())
                    .orElseThrow(() ->
                            new NegocioException(
                                    "Documento de legajo no existe"));
        }

        Reconocimiento entity =
                new Reconocimiento();

        entity.setEmpleadoId(
                empleado.getId());

        entity.setTipoReconocimiento(
                dto.getTipoReconocimiento());

        entity.setDescripcion(
                dto.getDescripcion());

        entity.setFechaReconocimiento(
                dto.getFechaReconocimiento());

        entity.setLegajoDocumentoId(
                dto.getLegajoDocumentoId());

        entity.setActivo(1);

        entity.setCreatedAt(
                LocalDateTime.now());

        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ReconocimientoResponseDto>
    listarPorEmpleado(
            Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivoOrderByFechaReconocimientoDesc(
                        empleadoId,
                        1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void eliminar(
            Long id) {

        Reconocimiento entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Reconocimiento no encontrado"));

        entity.setActivo(0);

        repository.save(entity);
    }

    private ReconocimientoResponseDto toDto(
            Reconocimiento entity) {

        ReconocimientoResponseDto dto =
                new ReconocimientoResponseDto();

        dto.setId(
                entity.getId());

        dto.setEmpleadoId(
                entity.getEmpleadoId());

        dto.setTipoReconocimiento(
                entity.getTipoReconocimiento());

        dto.setDescripcion(
                entity.getDescripcion());

        dto.setFechaReconocimiento(
                entity.getFechaReconocimiento());

        dto.setLegajoDocumentoId(
                entity.getLegajoDocumentoId());

        return dto;
    }
}