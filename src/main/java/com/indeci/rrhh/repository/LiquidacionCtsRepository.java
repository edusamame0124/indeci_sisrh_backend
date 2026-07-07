package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.LiquidacionCts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Feature 016 — Liquidaciones de CTS Trunca. La UK (EMPLEADO_PLANILLA_ID, PERIODO)
 * garantiza una sola liquidación por vínculo/período (anti-doble-liquidación).
 */
public interface LiquidacionCtsRepository extends JpaRepository<LiquidacionCts, Long> {

    Optional<LiquidacionCts> findByEmpleadoPlanillaIdAndPeriodo(
            Long empleadoPlanillaId, String periodo);

    List<LiquidacionCts> findByPeriodoAndRegimenLaboralId(
            String periodo, Long regimenLaboralId);
}
