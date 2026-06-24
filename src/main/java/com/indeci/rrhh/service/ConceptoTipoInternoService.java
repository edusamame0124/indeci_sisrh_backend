package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.ConceptoTipoInternoDto;
import com.indeci.rrhh.entity.ConceptoTipoInterno;
import com.indeci.rrhh.repository.ConceptoTipoInternoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SPEC_CONCEPTOS_PLANILLA §13 — catálogo "Tipo de Concepto" (taxonomía SISPER).
 *
 * <p>Solo lectura: alimenta el select del wizard de conceptos. La derivación al
 * {@code TIPO_CONCEPTO} del motor la hace {@link ConceptoPlanillaService} usando
 * {@code clasificacionMotor} de la fila elegida.</p>
 */
@Service
@RequiredArgsConstructor
public class ConceptoTipoInternoService {

    private final ConceptoTipoInternoRepository repository;

    /** Catálogo activo ordenado por {@code ORDEN}. */
    public List<ConceptoTipoInternoDto> listar() {
        return repository.findByActivoOrderByOrden(1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private ConceptoTipoInternoDto toDto(ConceptoTipoInterno e) {
        ConceptoTipoInternoDto dto = new ConceptoTipoInternoDto();
        dto.setCodigo(e.getCodigo());
        dto.setNombre(e.getNombre());
        dto.setClasificacionMotor(e.getClasificacionMotor());
        dto.setOrden(e.getOrden());
        return dto;
    }
}
