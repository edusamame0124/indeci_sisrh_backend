package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.ConceptoRtps;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SPEC_CONCEPTOS_PLANILLA P1 — repositorio del catálogo RTPS (PDT 601).
 */
public interface ConceptoRtpsRepository
        extends JpaRepository<ConceptoRtps, String> {

    /** Catálogo completo activo, ordenado por {@code ORDEN} (grupos e items intercalados). */
    List<ConceptoRtps> findByActivoOrderByOrden(Integer activo);

    /** Por tipo de fila: {@code esGrupo='N'} = items seleccionables; 'S' = cabeceras. */
    List<ConceptoRtps> findByEsGrupoAndActivoOrderByOrden(String esGrupo, Integer activo);
}
