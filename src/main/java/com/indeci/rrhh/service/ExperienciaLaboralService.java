package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ExperienciaLaboralDto;
import com.indeci.rrhh.dto.ExperienciaLaboralResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.ExperienciaLaboral;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.ExperienciaLaboralRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExperienciaLaboralService {

    private final ExperienciaLaboralRepository repository;

    private final EmpleadoRepository empleadoRepository;

    private final LegajoDocumentoRepository
            legajoDocumentoRepository;

    @Transactional
    public void registrar(
            ExperienciaLaboralDto dto) {

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

        ExperienciaLaboral entity =
                new ExperienciaLaboral();

        entity.setEmpleadoId(
                empleado.getId());

        entity.setEmpresa(
                dto.getEmpresa());

        entity.setCargo(
                dto.getCargo());

        entity.setFechaInicio(
                dto.getFechaInicio());

        entity.setFechaFin(
                dto.getFechaFin());

        entity.setFunciones(
                dto.getFunciones());

        entity.setLegajoDocumentoId(
                dto.getLegajoDocumentoId());

        entity.setActivo(1);

        entity.setCreatedAt(
                LocalDateTime.now());

        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ExperienciaLaboralResponseDto>
    listarPorEmpleado(
            Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivoOrderByFechaFinDesc(
                        empleadoId,
                        1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void eliminar(
            Long id) {

        ExperienciaLaboral entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Experiencia laboral no encontrada"));

        entity.setActivo(0);

        repository.save(entity);
    }

    private ExperienciaLaboralResponseDto toDto(
            ExperienciaLaboral entity) {

        ExperienciaLaboralResponseDto dto =
                new ExperienciaLaboralResponseDto();

        dto.setId(entity.getId());
        dto.setEmpleadoId(entity.getEmpleadoId());
        dto.setEmpresa(entity.getEmpresa());
        dto.setCargo(entity.getCargo());
        dto.setFechaInicio(entity.getFechaInicio());
        dto.setFechaFin(entity.getFechaFin());
        dto.setFunciones(entity.getFunciones());
        dto.setLegajoDocumentoId(entity.getLegajoDocumentoId());

        return dto;
    }
}