package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AsistenciaCabecera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AsistenciaCabeceraRepository
        extends JpaRepository<AsistenciaCabecera, Long> {

    Optional<AsistenciaCabecera>
    findByEmpleadoIdAndPeriodoAndActivo(
            Long empleadoId,
            String periodo,
            Integer activo);

    @Query("SELECT c.empleadoId FROM AsistenciaCabecera c WHERE c.periodo = :periodo AND c.activo = :activo AND c.empleadoId IN :empleadosIds")
    java.util.Set<Long> findEmpleadosIdsConCabeceraActiva(
            @Param("empleadosIds") java.util.Collection<Long> empleadosIds,
            @Param("periodo") String periodo,
            @Param("activo") Integer activo);

    List<AsistenciaCabecera>
    findByPeriodoAndActivo(
            String periodo,
            Integer activo);

    List<AsistenciaCabecera>
    findByImportacionIdAndActivo(
            Long importacionId,
            Integer activo);

    /** F5 — todas las versiones (activas e inactivas) de un empleado+periodo, recientes primero. */
    List<AsistenciaCabecera>
    findByEmpleadoIdAndPeriodoOrderByVersionDesc(Long empleadoId, String periodo);

    /** F5 — versión máxima existente para el empleado+periodo (NULL si no hay). */
    @Query("SELECT MAX(c.version) FROM AsistenciaCabecera c "
            + "WHERE c.empleadoId = :empleadoId AND c.periodo = :periodo")
    Integer maxVersion(@Param("empleadoId") Long empleadoId, @Param("periodo") String periodo);

    /** Historial — cabeceras activas de la importación que aún NO están en el estado dado. */
    long countByImportacionIdAndActivoAndEstadoNot(Long importacionId, Integer activo, String estado);

    long countByImportacionIdAndActivo(Long importacionId, Integer activo);

    /** Historial (badge tri-estado) — cabeceras activas de la importación en un estado dado. */
    long countByImportacionIdAndActivoAndEstado(Long importacionId, Integer activo, String estado);

    /** Historial (badge tri-estado) — cabeceras activas de la importación en cualquiera de los estados. */
    long countByImportacionIdAndActivoAndEstadoIn(
            Long importacionId, Integer activo, java.util.Collection<String> estados);
}
