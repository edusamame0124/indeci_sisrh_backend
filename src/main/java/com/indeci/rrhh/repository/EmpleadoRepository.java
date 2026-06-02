package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Empleado;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    Optional<Empleado> findByPersonaId(Long personaId);

    /** F3.3 — empleados por estado (uso típico: ACTIVO para preflight). */
    List<Empleado> findByEstado(String estado);
}
