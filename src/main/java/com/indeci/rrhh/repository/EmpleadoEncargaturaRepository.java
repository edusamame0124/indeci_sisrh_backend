package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.EmpleadoEncargatura;

public interface EmpleadoEncargaturaRepository
        extends JpaRepository<EmpleadoEncargatura, Long> {

    List<EmpleadoEncargatura> findByEmpleadoTitularIdAndEstado(
            Long empleadoTitularId, String estado);

    List<EmpleadoEncargatura> findByEmpleadoEncargIdAndEstado(
            Long empleadoEncargId, String estado);

    /** F5.2 — todas las encargaturas, ordenadas por fechaInicio desc para la tabla. */
    List<EmpleadoEncargatura> findAllByOrderByFechaInicioDesc();

    /** F5.2 — encargaturas por estado, ordenadas por fechaInicio desc. */
    List<EmpleadoEncargatura> findByEstadoOrderByFechaInicioDesc(String estado);

    /**
     * F5.2 — Detecta solape entre la encargatura nueva y otras ACTIVAS del
     * mismo reemplazante. Una encargatura sin fechaFin se considera abierta
     * hasta {@code LocalDate.MAX} (se trata como solape garantizado).
     */
    @Query("""
            SELECT e
              FROM EmpleadoEncargatura e
             WHERE e.empleadoEncargId = :encargId
               AND e.estado           = 'ACTIVO'
               AND e.fechaInicio     <= :fechaFin
               AND (e.fechaFin IS NULL OR e.fechaFin >= :fechaInicio)
               AND (:idExcluir IS NULL OR e.id <> :idExcluir)
            """)
    List<EmpleadoEncargatura> findSolapesActivos(
            @Param("encargId") Long empleadoEncargId,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin,
            @Param("idExcluir") Long idExcluir);
}
