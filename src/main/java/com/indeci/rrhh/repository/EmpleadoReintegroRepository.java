package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.EmpleadoReintegro;

/**
 * F1.4 Motor v3 — Acceso a INDECI_EMPLEADO_REINTEGRO.
 *
 * Lookup principal: {@link #findByEmpleadoIdAndPeriodoAndActivo} — devuelve
 * la fila vigente que el motor PASO 5b consume. El UK
 * (EMPLEADO_ID, PERIODO) garantiza un único reintegro por período.
 */
public interface EmpleadoReintegroRepository
        extends JpaRepository<EmpleadoReintegro, Long> {

    Optional<EmpleadoReintegro> findByEmpleadoIdAndPeriodoAndActivo(
            Long empleadoId,
            String periodo,
            Integer activo);

    List<EmpleadoReintegro> findByEmpleadoIdAndActivoOrderByCreatedAtDesc(
            Long empleadoId,
            Integer activo);
}
