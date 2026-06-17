package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.CapacitacionDto;
import com.indeci.rrhh.dto.CapacitacionResponseDto;
import com.indeci.rrhh.entity.Capacitacion;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.repository.CapacitacionRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CapacitacionService {

    private final CapacitacionRepository repository;

    private final EmpleadoRepository empleadoRepository;

    private final LegajoDocumentoRepository
            legajoDocumentoRepository;

    @Transactional
    public void registrar(
            CapacitacionDto dto) {

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

        Capacitacion entity =
                new Capacitacion();

        entity.setEmpleadoId(
                empleado.getId());

        entity.setNombreCurso(
                dto.getNombreCurso());

        entity.setInstitucion(
                dto.getInstitucion());

        entity.setHoras(
                dto.getHoras());

        entity.setFechaInicio(
                dto.getFechaInicio());

        entity.setFechaFin(
                dto.getFechaFin());

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
    public List<CapacitacionResponseDto>
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

        Capacitacion entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Registro no encontrado"));

        entity.setActivo(0);

        repository.save(entity);
    }

    private CapacitacionResponseDto
    toDto(
            Capacitacion entity) {

        CapacitacionResponseDto dto =
                new CapacitacionResponseDto();

        dto.setId(
                entity.getId());

        dto.setEmpleadoId(
                entity.getEmpleadoId());

        dto.setNombreCurso(
                entity.getNombreCurso());

        dto.setInstitucion(
                entity.getInstitucion());

        dto.setHoras(
                entity.getHoras());

        dto.setFechaInicio(
                entity.getFechaInicio());

        dto.setFechaFin(
                entity.getFechaFin());

        dto.setCertificado(
                entity.getCertificado());

        dto.setLegajoDocumentoId(
                entity.getLegajoDocumentoId());

        return dto;
    }
}