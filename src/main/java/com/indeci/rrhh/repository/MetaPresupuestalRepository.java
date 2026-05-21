package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.MetaPresupuestal;

/**
 * Spec 012 / C1 · P-05 — Certificaciones presupuestales por meta.
 */
public interface MetaPresupuestalRepository
        extends JpaRepository<MetaPresupuestal, Long> {

    List<MetaPresupuestal> findByPeriodoIdAndActivo(Long periodoId, Integer activo);

    Optional<MetaPresupuestal> findByPeriodoIdAndMetaAndActivo(
            Long periodoId, String meta, Integer activo);
}
