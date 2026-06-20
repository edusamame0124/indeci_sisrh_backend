package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpleadoSaludEps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmpleadoSaludEpsRepository extends JpaRepository<EmpleadoSaludEps, Long> {

    List<EmpleadoSaludEps> findByEmpleadoIdOrderByFechaInicioDesc(Long empleadoId);

    Optional<EmpleadoSaludEps> findFirstByEmpleadoIdAndEstadoOrderByFechaInicioDesc(
            Long empleadoId, String estado);

    @Query("""
        SELECT COUNT(e) FROM EmpleadoSaludEps e
        WHERE e.empleadoId = :empId
          AND e.estado IN ('ACTIVO', 'CERRADO')
          AND (:idExcluir IS NULL OR e.id <> :idExcluir)
          AND (e.fechaFin IS NULL OR e.fechaFin >= :inicio)
          AND e.fechaInicio <= COALESCE(:fin, e.fechaInicio)
        """)
    long countSolapamiento(@Param("empId") Long empleadoId,
                            @Param("inicio") LocalDate inicio,
                            @Param("fin") LocalDate fin,
                            @Param("idExcluir") Long idExcluir);

    @Query("""
        SELECT e FROM EmpleadoSaludEps e
        WHERE e.empleadoId = :empId
          AND e.estado = 'ACTIVO'
          AND e.fechaInicio <= :fecha
          AND (e.fechaFin IS NULL OR e.fechaFin >= :fecha)
        ORDER BY e.fechaInicio DESC
        """)
    List<EmpleadoSaludEps> findActivaByEmpleadoYFecha(@Param("empId") Long empleadoId,
                                                       @Param("fecha") LocalDate fecha);
}
