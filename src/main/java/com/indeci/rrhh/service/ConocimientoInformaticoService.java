package com.indeci.rrhh.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ConocimientoInformaticoDto;
import com.indeci.rrhh.dto.ConocimientoInformaticoResponseDto;
import com.indeci.rrhh.entity.ConocimientoInformatico;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.repository.ConocimientoInformaticoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;


import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConocimientoInformaticoService {

    private final ConocimientoInformaticoRepository repository;

    private final EmpleadoRepository empleadoRepository;

    private final LegajoDocumentoRepository
            legajoDocumentoRepository;

    @Transactional
    public void registrar(
            ConocimientoInformaticoDto dto) {

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

        ConocimientoInformatico entity =
                new ConocimientoInformatico();

        entity.setEmpleadoId(
                empleado.getId());

        entity.setHerramienta(
                dto.getHerramienta());

        entity.setNivel(
                dto.getNivel());

        entity.setCertificado(
                dto.getCertificado());

        entity.setLegajoDocumentoId(
                dto.getLegajoDocumentoId());

        entity.setActivo(1);

        entity.setCreatedAt(
                LocalDateTime.now());

        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ConocimientoInformaticoResponseDto>
    listarPorEmpleado(
            Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivoOrderByHerramientaAsc(
                        empleadoId,
                        1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void eliminar(
            Long id) {

        ConocimientoInformatico entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Conocimiento informático no encontrado"));

        entity.setActivo(0);

        repository.save(entity);
    }

    private ConocimientoInformaticoResponseDto toDto(
            ConocimientoInformatico entity) {

        ConocimientoInformaticoResponseDto dto =
                new ConocimientoInformaticoResponseDto();

        dto.setId(entity.getId());
        dto.setEmpleadoId(entity.getEmpleadoId());
        dto.setHerramienta(entity.getHerramienta());
        dto.setNivel(entity.getNivel());
        dto.setCertificado(entity.getCertificado());
        dto.setLegajoDocumentoId(entity.getLegajoDocumentoId());

        return dto;
    }
}