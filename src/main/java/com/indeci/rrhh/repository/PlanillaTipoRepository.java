package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.PlanillaTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — repositorio del catálogo de tipos de planilla.
 */
public interface PlanillaTipoRepository extends JpaRepository<PlanillaTipo, String> {

    /** Catálogo activo ordenado por {@code ORDEN} (para el multiselect del wizard). */
    List<PlanillaTipo> findByActivoOrderByOrden(Integer activo);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(p.orden) FROM PlanillaTipo p")
    Integer findMaxOrden();
}
