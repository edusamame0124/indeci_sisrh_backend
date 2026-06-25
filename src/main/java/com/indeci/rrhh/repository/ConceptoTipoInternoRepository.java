package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.ConceptoTipoInterno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SPEC_CONCEPTOS_PLANILLA §13 — repositorio del catálogo "Tipo de Concepto" (SISPER).
 */
public interface ConceptoTipoInternoRepository
        extends JpaRepository<ConceptoTipoInterno, String> {

    /** Catálogo activo ordenado por {@code ORDEN} (para el select del wizard). */
    List<ConceptoTipoInterno> findByActivoOrderByOrden(Integer activo);
}
