package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MedidaDisciplinariaDto;
import com.indeci.rrhh.dto.MedidaDisciplinariaResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.MedidaDisciplinaria;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;
import com.indeci.rrhh.repository.MedidaDisciplinariaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MedidaDisciplinariaService {

    private final MedidaDisciplinariaRepository repository;

    private final EmpleadoRepository empleadoRepository;

    private final LegajoDocumentoRepository
            legajoDocumentoRepository;

    @Transactional
    public void registrar(
            MedidaDisciplinariaDto dto) {

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

        MedidaDisciplinaria entity =
                new MedidaDisciplinaria();

        entity.setEmpleadoId(
                empleado.getId());

        entity.setTipoMedida(
                dto.getTipoMedida());

        entity.setDescripcion(
                dto.getDescripcion());

        entity.setFechaInicio(
                dto.getFechaInicio());

        entity.setFechaFin(
                dto.getFechaFin());

        entity.setLegajoDocumentoId(
                dto.getLegajoDocumentoId());

        entity.setActivo(1);

        entity.setCreatedAt(
                LocalDateTime.now());

        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<MedidaDisciplinariaResponseDto>
    listarPorEmpleado(
            Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivoOrderByFechaInicioDesc(
                        empleadoId,
                        1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void eliminar(
            Long id) {

        MedidaDisciplinaria entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Medida disciplinaria no encontrada"));

        entity.setActivo(0);

        repository.save(entity);
    }

    private MedidaDisciplinariaResponseDto toDto(
            MedidaDisciplinaria entity) {

        MedidaDisciplinariaResponseDto dto =
                new MedidaDisciplinariaResponseDto();

        dto.setId(
                entity.getId());

        dto.setEmpleadoId(
                entity.getEmpleadoId());

        dto.setTipoMedida(
                entity.getTipoMedida());

        dto.setDescripcion(
                entity.getDescripcion());

        dto.setFechaInicio(
                entity.getFechaInicio());

        dto.setFechaFin(
                entity.getFechaFin());

        dto.setLegajoDocumentoId(
                entity.getLegajoDocumentoId());

        return dto;
    }
}