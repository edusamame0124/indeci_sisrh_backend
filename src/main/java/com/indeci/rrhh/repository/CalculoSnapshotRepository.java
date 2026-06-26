package com.indeci.rrhh.repository;

import java.math.BigDecimal;
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

    /**
     * Acumulado conocido por INDECI: suma de la base afecta 4ta ({@code baseCalculo}
     * de los snapshots {@code IR4TA_CAS} vigentes) del empleado en el año fiscal.
     *
     * <p>El año se identifica por el prefijo del período (sirve para "YYYYMM" y
     * "YYYY-MM"). Cuando {@code periodoExcluyenteDesde} no es null, solo cuenta
     * períodos ESTRICTAMENTE anteriores a ese valor (separa acumulado de la
     * proyección del período actual). Devuelve 0 si no hay filas.</p>
     */
    @Query("""
        SELECT COALESCE(SUM(s.baseCalculo), 0) FROM CalculoSnapshot s
        WHERE s.empleadoId = :empleadoId
          AND s.regla = 'IR4TA_CAS'
          AND s.activo = 1
          AND s.periodo LIKE :anioPrefijo
          AND (:periodoExcluyenteDesde IS NULL OR s.periodo < :periodoExcluyenteDesde)
        """)
    BigDecimal sumarBaseIr4taPorAnio(
            @Param("empleadoId") Long empleadoId,
            @Param("anioPrefijo") String anioPrefijo,
            @Param("periodoExcluyenteDesde") String periodoExcluyenteDesde);

    @Query("""
        SELECT s FROM CalculoSnapshot s
        WHERE s.empleadoId = :empleadoId
          AND s.regla = 'IR4TA_CAS'
          AND s.activo = 1
          AND s.periodo LIKE :anioPrefijo
        ORDER BY s.periodo ASC
        """)
    List<CalculoSnapshot> findIr4taCasVigentesPorAnio(
            @Param("empleadoId") Long empleadoId,
            @Param("anioPrefijo") String anioPrefijo);
}
