package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.FormacionAcademicaDto;
import com.indeci.rrhh.dto.FormacionAcademicaResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.FormacionAcademica;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.FormacionAcademicaRepository;
import com.indeci.rrhh.repository.GradoAcademicoRepository;
import com.indeci.rrhh.repository.LegajoDocumentoRepository;
import com.indeci.rrhh.repository.NivelInstruccionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormacionAcademicaService {

    private final FormacionAcademicaRepository repository;
    private final EmpleadoRepository empleadoRepository;
    private final NivelInstruccionRepository nivelInstruccionRepository;

    private final GradoAcademicoRepository gradoAcademicoRepository;
    
    private final LegajoDocumentoRepository
    legajoDocumentoRepository;

    @Transactional
    public void registrar(
            FormacionAcademicaDto dto) {

        Empleado empleado =
                empleadoRepository
                        .findById(dto.getEmpleadoId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Empleado no existe"));

nivelInstruccionRepository
        .findById(dto.getNivelInstruccionId())
        .orElseThrow(() ->
                new NegocioException(
                        "Nivel de instrucción no existe"));

gradoAcademicoRepository
        .findById(dto.getGradoAcademicoId())
        .orElseThrow(() ->
                new NegocioException(
                        "Grado académico no existe"));


        FormacionAcademica entity =
                new FormacionAcademica();
        
        

        entity.setEmpleadoId(
                empleado.getId());

        entity.setNivelInstruccionId(
                dto.getNivelInstruccionId());

        entity.setGradoAcademicoId(
                dto.getGradoAcademicoId());

        entity.setInstitucion(
                dto.getInstitucion());

        entity.setCarrera(
                dto.getCarrera());

        entity.setFechaInicio(
                dto.getFechaInicio());

        entity.setFechaFin(
                dto.getFechaFin());

        entity.setEgresado(
                dto.getEgresado());

        entity.setBachiller(
                dto.getBachiller());

        entity.setTitulado(
                dto.getTitulado());

        entity.setNroTitulo(
                dto.getNroTitulo());
        
        if(dto.getLegajoDocumentoId() != null){

            legajoDocumentoRepository
                    .findById(
                            dto.getLegajoDocumentoId())
                    .orElseThrow(() ->
                            new NegocioException(
                                    "Documento de legajo no existe"));
        }

        entity.setLegajoDocumentoId(
                dto.getLegajoDocumentoId());

        entity.setActivo(1);

        entity.setCreatedAt(
                LocalDateTime.now());

        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<FormacionAcademicaResponseDto>
    listarPorEmpleado(Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivoOrderByFechaFinDesc(
                        empleadoId,
                        1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void eliminar(Long id) {

        FormacionAcademica entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Registro no encontrado"));

        entity.setActivo(0);

        repository.save(entity);
    }

    private FormacionAcademicaResponseDto toDto(
            FormacionAcademica entity) {

        FormacionAcademicaResponseDto dto =
                new FormacionAcademicaResponseDto();

        dto.setId(entity.getId());
        dto.setEmpleadoId(entity.getEmpleadoId());
        dto.setNivelInstruccionId(
                entity.getNivelInstruccionId());

        if(entity.getNivelInstruccion() != null) {

            dto.setNivelInstruccion(
                    entity.getNivelInstruccion()
                            .getNombre());
        }

        dto.setGradoAcademicoId(
                entity.getGradoAcademicoId());

        if(entity.getGradoAcademico() != null) {

            dto.setGradoAcademico(
                    entity.getGradoAcademico()
                            .getNombre());
        }
        dto.setInstitucion(entity.getInstitucion());
        dto.setCarrera(entity.getCarrera());
        dto.setFechaInicio(entity.getFechaInicio());
        dto.setFechaFin(entity.getFechaFin());
        dto.setEgresado(entity.getEgresado());
        dto.setBachiller(entity.getBachiller());
        dto.setTitulado(entity.getTitulado());
        dto.setNroTitulo(entity.getNroTitulo());
        dto.setLegajoDocumentoId(
                entity.getLegajoDocumentoId());

        return dto;
    }
}