package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Empleado;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    Optional<Empleado> findByPersonaId(Long personaId);

    /** F3.3 — empleados por estado (uso típico: ACTIVO para preflight). */
    List<Empleado> findByEstado(String estado);

    /**
     * Resumen persona por empleadoId — evita N+1 al listar eventos del período.
     * Columnas: [0]=empleadoId [1]=nombreCompleto [2]=dni
     */
    @Query("""
            SELECT e.id, p.nombreCompleto, p.dni
              FROM Empleado e
              JOIN Persona p ON p.id = e.personaId
             WHERE e.id IN :empleadoIds
            """)
    List<Object[]> findPersonaResumenByEmpleadoIds(
            @Param("empleadoIds") Collection<Long> empleadoIds);
}
