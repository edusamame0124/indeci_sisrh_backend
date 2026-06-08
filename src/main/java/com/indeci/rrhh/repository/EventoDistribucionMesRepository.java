package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EventoDistribucionMes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventoDistribucionMesRepository
        extends JpaRepository<EventoDistribucionMes, Long> {

    List<EventoDistribucionMes> findByEmpleadoEventoIdOrderByFechaDesdeAsc(Long empleadoEventoId);

    void deleteByEmpleadoEventoId(Long empleadoEventoId);

    @Query("""
            SELECT d
              FROM EventoDistribucionMes d
             WHERE d.empleadoEventoId IN (
                   SELECT e.id FROM EmpleadoEvento e
                    WHERE e.empleadoId = :empleadoId AND e.activo = 1)
               AND d.periodo = :periodo
               AND d.afectaDiasLaborados = 'S'
            """)
    List<EventoDistribucionMes> findTramosDiasLaboradosPorEmpleadoYPeriodo(
            @Param("empleadoId") Long empleadoId,
            @Param("periodo") String periodo);

    @Query("""
            SELECT COUNT(DISTINCT d.periodo)
              FROM EventoDistribucionMes d
             WHERE d.empleadoEventoId = :eventoId
            """)
    long countDistinctPeriodosByEmpleadoEventoId(@Param("eventoId") Long eventoId);

    @Query("""
            SELECT d.empleadoEventoId
              FROM EventoDistribucionMes d
             WHERE d.empleadoEventoId IN (
                   SELECT e.id FROM EmpleadoEvento e
                    WHERE e.empleadoId = :empleadoId AND e.activo = 1)
               AND d.periodo = :periodo
            """)
    List<Long> findEventoIdsConTramoEnPeriodo(
            @Param("empleadoId") Long empleadoId,
            @Param("periodo") String periodo);
}
