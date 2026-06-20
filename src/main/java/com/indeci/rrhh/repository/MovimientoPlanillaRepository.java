package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MovimientoPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovimientoPlanillaRepository
        extends JpaRepository<MovimientoPlanilla, Long> {

    Optional<MovimientoPlanilla>
    findByEmpleadoIdAndPeriodoAndActivo(
            Long empleadoId,
            String periodo,
            Integer activo);

    List<MovimientoPlanilla>
    findByPeriodoAndActivo(
            String periodo,
            Integer activo);

    /** Historial de movimientos de un empleado en todos los períodos (PANTALLA-08). */
    List<MovimientoPlanilla>
    findByEmpleadoIdAndActivo(
            Long empleadoId,
            Integer activo);

    /** B1/B2 — IDs de vigencias AFP distintos usados en los movimientos de un período. */
    @Query("SELECT DISTINCT m.afpParamVigenciaId FROM MovimientoPlanilla m " +
           "WHERE m.periodo = :periodo AND m.afpParamVigenciaId IS NOT NULL")
    List<Long> findDistinctAfpVigenciaIdsByPeriodo(@Param("periodo") String periodo);

    /** B1/B2 — IDs de vigencias ONP distintos usados en los movimientos de un período. */
    @Query("SELECT DISTINCT m.onpParamVigenciaId FROM MovimientoPlanilla m " +
           "WHERE m.periodo = :periodo AND m.onpParamVigenciaId IS NOT NULL")
    List<Long> findDistinctOnpVigenciaIdsByPeriodo(@Param("periodo") String periodo);
}