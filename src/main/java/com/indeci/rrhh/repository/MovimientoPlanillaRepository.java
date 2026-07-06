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
    findFirstByEmpleadoIdAndPeriodoAndActivoOrderByIdDesc(
            Long empleadoId,
            String periodo,
            Integer activo);

    /**
     * Track B — Movimientos REGULARES (NO AGUINALDO) del empleado en el período,
     * más recientes primero. El proceso AGUINALDO se genera como movimiento aparte
     * y NUNCA debe ser tomado por el motor regular ni por queries que asumen un
     * único movimiento por empleado+período (excepción quirúrgica autorizada al
     * constraint #7). El histórico de renta 5ta usa {@code findByEmpleadoIdAndActivo}
     * (lista, todos los períodos) y SÍ incluye el aguinaldo — ese no se toca.
     */
    @Query("SELECT m FROM MovimientoPlanilla m "
            + "WHERE m.empleadoId = :empleadoId AND m.periodo = :periodo AND m.activo = :activo "
            + "AND (m.tipoPlanilla IS NULL OR m.tipoPlanilla <> 'AGUINALDO') "
            + "ORDER BY m.id DESC")
    List<MovimientoPlanilla> findRegularesByEmpleadoIdAndPeriodoAndActivo(
            @Param("empleadoId") Long empleadoId,
            @Param("periodo") String periodo,
            @Param("activo") Integer activo);

    /**
     * Finder de un único movimiento por empleado+período usado por el motor regular:
     * devuelve el REGULAR más reciente, EXCLUYENDO explícitamente el AGUINALDO.
     */
    default Optional<MovimientoPlanilla> findByEmpleadoIdAndPeriodoAndActivo(
            Long empleadoId, String periodo, Integer activo) {
        return findRegularesByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, activo)
                .stream().findFirst();
    }

    Optional<MovimientoPlanilla>
    findByEmpleadoIdAndPeriodoAndTipoPlanillaAndEmpleadoPuestoIdAndActivo(
            Long empleadoId,
            String periodo,
            String tipoPlanilla,
            Long empleadoPuestoId,
            Integer activo);

    List<MovimientoPlanilla>
    findByPeriodoAndActivo(
            String periodo,
            Integer activo);

    /** Track B — todos los movimientos del empleado en el período (regular + AGUINALDO). */
    List<MovimientoPlanilla>
    findAllByEmpleadoIdAndPeriodoAndActivo(
            Long empleadoId,
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

    List<MovimientoPlanilla> findByLoteId(Long loteId);
}