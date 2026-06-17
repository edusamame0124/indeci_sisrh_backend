package com.indeci.rrhh.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.dto.TrayectoriaLaboralResponseDto;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrayectoriaLaboralService {

    private final EmpleadoPuestoRepository repository;

    @Transactional(readOnly = true)
    public List<TrayectoriaLaboralResponseDto>
    listarPorEmpleado(Long empleadoId) {

        return repository
                .findByEmpleadoIdOrderByFechaInicioDesc(
                        empleadoId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private TrayectoriaLaboralResponseDto toDto(
            EmpleadoPuesto entity) {

        TrayectoriaLaboralResponseDto dto =
                new TrayectoriaLaboralResponseDto();

        dto.setId(entity.getId());

        dto.setEmpleadoId(
                entity.getEmpleadoId());

        dto.setCargoId(
                entity.getCargoId());

        if(entity.getCargo() != null) {

            dto.setCargo(
                    entity.getCargo()
                            .getNombre());
        }

        dto.setOficinaId(
                entity.getOficinaId());

        if(entity.getOficina() != null) {

            dto.setOficina(
                    entity.getOficina()
                            .getNombre());
        }

        dto.setDependenciaId(
                entity.getDependenciaId());

        if(entity.getDependencia() != null) {

            dto.setDependencia(
                    entity.getDependencia()
                            .getNombre());
        }

        dto.setSedeId(
                entity.getSedeId());

        if(entity.getSede() != null) {

            dto.setSede(
                    entity.getSede()
                            .getNombre());
        }

        dto.setFechaInicio(
                entity.getFechaInicio());

        dto.setFechaFin(
                entity.getFechaFin());

        dto.setActivo(
                entity.getActivo());

        return dto;
    }
}