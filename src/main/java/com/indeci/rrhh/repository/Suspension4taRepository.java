package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.Suspension4ta;

/**
 * FASE 1 — Repositorio de constancias de suspensión de retención de 4ta (CAS).
 */
public interface Suspension4taRepository
        extends JpaRepository<Suspension4ta, Long> {

    /** Constancias de un empleado por estado (pantalla de mantenimiento). */
    List<Suspension4ta> findByEmpleadoIdAndEstadoOrderByFechaVigIniDesc(
            Long empleadoId, String estado);

    /**
     * Constancias ACTIVAS vigentes en {@code fecha}: FECHA_VIG_INI ≤ fecha ≤
     * NVL(FECHA_VIG_FIN, +∞). Ordenadas por inicio descendente (la más reciente
     * primero). Usada por el motor para decidir suspensión.
     */
    @Query("SELECT s FROM Suspension4ta s "
            + "WHERE s.empleadoId = :empleadoId AND s.estado = 'ACTIVO' "
            + "AND s.fechaVigIni <= :fecha "
            + "AND (s.fechaVigFin IS NULL OR s.fechaVigFin >= :fecha) "
            + "ORDER BY s.fechaVigIni DESC")
    List<Suspension4ta> findVigentes(
            @Param("empleadoId") Long empleadoId,
            @Param("fecha") LocalDate fecha);

    /**
     * P0 Planilla CAS Consolidada — constancias ACTIVAS vigentes en el período
     * para un conjunto de empleados (sin N+1). Una suspensión es vigente si su
     * rango [vigIni, vigFin] intersecta el mes [inicioPeriodo, finPeriodo].
     */
    @Query("SELECT s FROM Suspension4ta s "
            + "WHERE s.empleadoId IN :empleadoIds AND s.estado = 'ACTIVO' "
            + "AND s.fechaVigIni <= :finPeriodo "
            + "AND (s.fechaVigFin IS NULL OR s.fechaVigFin >= :inicioPeriodo) "
            + "ORDER BY s.fechaVigIni DESC")
    List<Suspension4ta> findVigentesEnPeriodo(
            @Param("empleadoIds") List<Long> empleadoIds,
            @Param("inicioPeriodo") LocalDate inicioPeriodo,
            @Param("finPeriodo") LocalDate finPeriodo);
}
