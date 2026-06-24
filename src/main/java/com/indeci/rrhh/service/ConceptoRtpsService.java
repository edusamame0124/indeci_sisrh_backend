package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.ConceptoRtpsDto;
import com.indeci.rrhh.entity.ConceptoRtps;
import com.indeci.rrhh.repository.ConceptoRtpsRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SPEC_CONCEPTOS_PLANILLA P1 — catálogo "Tipo Concepto RTPS" (PDT 601).
 *
 * <p>Devuelve el catálogo agrupado (grupos e items intercalados por {@code ORDEN}).
 * Solo los items ({@code esGrupo='N'}) son seleccionables; las cabeceras de grupo
 * ({@code esGrupo='S'}) se marcan {@code seleccionable=false} para que la UI las
 * muestre como encabezados no elegibles.</p>
 */
@Service
@RequiredArgsConstructor
public class ConceptoRtpsService {

    private static final String GRUPO = "S";

    private final ConceptoRtpsRepository repository;

    /** Catálogo activo ordenado; grupos no seleccionables, items seleccionables. */
    public List<ConceptoRtpsDto> listar() {
        return repository.findByActivoOrderByOrden(1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private ConceptoRtpsDto toDto(ConceptoRtps e) {
        ConceptoRtpsDto dto = new ConceptoRtpsDto();
        dto.setCodigo(e.getCodigo());
        dto.setDescripcion(e.getDescripcion());
        dto.setGrupoCodigo(e.getGrupoCodigo());
        dto.setGrupoDescripcion(e.getGrupoDescripcion());
        dto.setEsGrupo(e.getEsGrupo());
        dto.setOrden(e.getOrden());
        dto.setSeleccionable(!GRUPO.equalsIgnoreCase(e.getEsGrupo()));
        return dto;
    }
}
