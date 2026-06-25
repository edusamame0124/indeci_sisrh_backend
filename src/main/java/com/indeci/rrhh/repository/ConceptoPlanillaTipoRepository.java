package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.ConceptoPlanillaTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — repositorio de la asociación M:N
 * concepto ↔ tipo de planilla.
 */
public interface ConceptoPlanillaTipoRepository
        extends JpaRepository<ConceptoPlanillaTipo, Long> {

    /** Asociaciones de un concepto (para poblar el response y para clonar/versionar). */
    List<ConceptoPlanillaTipo> findByConceptoPlanillaId(Long conceptoPlanillaId);

    /** Reemplazo de asociaciones: borra las del concepto antes de insertar las nuevas. */
    void deleteByConceptoPlanillaId(Long conceptoPlanillaId);
}
