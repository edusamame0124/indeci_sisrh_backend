package com.indeci.rrhh.service;


import com.indeci.rrhh.dto.EmpleadoConceptoDto;
import com.indeci.rrhh.dto.EmpleadoConceptoResponseDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpleadoConceptoService {

    private final EmpleadoConceptoRepository repository;

    private final ConceptoPlanillaRepository
            conceptoRepository;

    public void guardar(
            EmpleadoConceptoDto dto) {

        EmpleadoConcepto nuevo =
                new EmpleadoConcepto();

        nuevo.setEmpleadoId(
                dto.getEmpleadoId());

        nuevo.setConceptoPlanillaId(
                dto.getConceptoPlanillaId());

        nuevo.setMonto(
                dto.getMonto());

        nuevo.setPorcentaje(
                dto.getPorcentaje());

        nuevo.setFormula(
                dto.getFormula());

        nuevo.setActivo(1);

        repository.save(nuevo);
    }

    public List<EmpleadoConceptoResponseDto>
    listarEmpleado(Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivo(
                        empleadoId,
                        1)
                .stream()
                .map(this::convertir)
                .collect(Collectors.toList());
    }

    private EmpleadoConceptoResponseDto
    convertir(EmpleadoConcepto e) {

        EmpleadoConceptoResponseDto dto =
                new EmpleadoConceptoResponseDto();

        dto.setId(e.getId());

        dto.setConceptoPlanillaId(
                e.getConceptoPlanillaId());

        dto.setMonto(
                e.getMonto());

        dto.setPorcentaje(
                e.getPorcentaje());

        dto.setFormula(
                e.getFormula());

        dto.setActivo(
                e.getActivo());

        if (e.getConceptoPlanillaId() != null) {

            ConceptoPlanilla concepto =
                    conceptoRepository
                            .findById(
                                    e.getConceptoPlanillaId())
                            .orElse(null);

            if (concepto != null) {

                dto.setConcepto(
                        concepto.getNombre());
            }
        }

        return dto;
    }
}