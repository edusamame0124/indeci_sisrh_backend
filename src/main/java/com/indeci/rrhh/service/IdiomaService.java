package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.IdiomaDto;
import com.indeci.rrhh.dto.IdiomaResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Idioma;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.IdiomaRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IdiomaService {

    private final IdiomaRepository repository;

    private final EmpleadoRepository empleadoRepository;

    private final LegajoDocumentoRepository
            legajoDocumentoRepository;

    @Transactional
    public void registrar(
            IdiomaDto dto) {

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

        Idioma entity =
                new Idioma();

        entity.setEmpleadoId(
                empleado.getId());

        entity.setIdioma(
                dto.getIdioma());

        entity.setNivelLectura(
                dto.getNivelLectura());

        entity.setNivelEscritura(
                dto.getNivelEscritura());

        entity.setNivelHabla(
                dto.getNivelHabla());

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
    public List<IdiomaResponseDto>
    listarPorEmpleado(
            Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivoOrderByIdiomaAsc(
                        empleadoId,
                        1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void eliminar(
            Long id) {

        Idioma entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Idioma no encontrado"));

        entity.setActivo(0);

        repository.save(entity);
    }

    private IdiomaResponseDto toDto(
            Idioma entity) {

        IdiomaResponseDto dto =
                new IdiomaResponseDto();

        dto.setId(
                entity.getId());

        dto.setEmpleadoId(
                entity.getEmpleadoId());

        dto.setIdioma(
                entity.getIdioma());

        dto.setNivelLectura(
                entity.getNivelLectura());

        dto.setNivelEscritura(
                entity.getNivelEscritura());

        dto.setNivelHabla(
                entity.getNivelHabla());

        dto.setCertificado(
                entity.getCertificado());

        dto.setLegajoDocumentoId(
                entity.getLegajoDocumentoId());

        return dto;
    }
}