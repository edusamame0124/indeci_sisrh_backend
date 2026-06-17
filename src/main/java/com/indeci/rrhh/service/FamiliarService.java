package com.indeci.rrhh.service;


import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.FamiliarDto;
import com.indeci.rrhh.dto.FamiliarResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Familiar;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.FamiliarRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FamiliarService {

    private final FamiliarRepository repository;

    private final EmpleadoRepository empleadoRepository;

    @Transactional
    public void registrar(
            FamiliarDto dto) {

        Empleado empleado =
                empleadoRepository
                        .findById(dto.getEmpleadoId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Empleado no existe"));

        Familiar entity =
                new Familiar();

        entity.setEmpleadoId(
                empleado.getId());

        entity.setNombreCompleto(
                dto.getNombreCompleto());

        entity.setParentesco(
                dto.getParentesco());

        entity.setFechaNacimiento(
                dto.getFechaNacimiento());

        entity.setTipoDocumentoId(
                dto.getTipoDocumentoId());

        entity.setNroDocumento(
                dto.getNroDocumento());

        entity.setTelefono(
                dto.getTelefono());

        entity.setActivo(1);

        entity.setCreatedAt(
                LocalDateTime.now());

        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<FamiliarResponseDto>
    listarPorEmpleado(
            Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivo(
                        empleadoId,
                        1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void eliminar(
            Long id) {

        Familiar entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Familiar no encontrado"));

        entity.setActivo(0);

        repository.save(entity);
    }

    private FamiliarResponseDto toDto(
            Familiar entity) {

        FamiliarResponseDto dto =
                new FamiliarResponseDto();

        dto.setId(
                entity.getId());

        dto.setEmpleadoId(
                entity.getEmpleadoId());

        dto.setNombreCompleto(
                entity.getNombreCompleto());

        dto.setParentesco(
                entity.getParentesco());

        dto.setFechaNacimiento(
                entity.getFechaNacimiento());

        dto.setTipoDocumentoId(
                entity.getTipoDocumentoId());

        dto.setNroDocumento(
                entity.getNroDocumento());

        dto.setTelefono(
                entity.getTelefono());

        return dto;
    }
}