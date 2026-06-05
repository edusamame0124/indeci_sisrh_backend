package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.CalculoSnapshot;

/**
 * FASE 2 — Repositorio del snapshot de trazabilidad del cálculo de planilla.
 */
public interface CalculoSnapshotRepository
        extends JpaRepository<CalculoSnapshot, Long> {

    /**
     * Snapshots vigentes de un empleado en un período, ordenados para mostrar
     * primero el contexto (GENERAL) y luego las reglas. Usado por la pantalla
     * de explicación (solo lectura).
     */
    List<CalculoSnapshot> findByEmpleadoIdAndPeriodoAndActivoOrderByReglaAscIdAsc(
            Long empleadoId, String periodo, Integer activo);

    /** P0 Planilla CAS Consolidada — snapshots vigentes del período en batch (sin N+1). */
    List<CalculoSnapshot> findByEmpleadoIdInAndPeriodoAndActivo(
            List<Long> empleadoIds, String periodo, Integer activo);

    /**
     * Desactiva (ACTIVO=0) todos los snapshots vigentes de un empleado para un
     * período. Se invoca antes de regenerar para garantizar reproducibilidad
     * (un solo conjunto vigente por par empleado/período).
     */
    @Modifying
    @Query("UPDATE CalculoSnapshot s SET s.activo = 0 "
            + "WHERE s.empleadoId = :empleadoId AND s.periodo = :periodo AND s.activo = 1")
    int desactivarVigentes(
            @Param("empleadoId") Long empleadoId,
            @Param("periodo") String periodo);
}
