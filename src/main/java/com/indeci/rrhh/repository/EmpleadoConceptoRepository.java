package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpleadoConcepto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpleadoConceptoRepository
        extends JpaRepository<EmpleadoConcepto, Long> {

    List<EmpleadoConcepto>
    findByEmpleadoIdAndActivo(
            Long empleadoId,
            Integer activo);

    /** Spec 012 / C3 (BKD-006) — paso «conceptos» del flujo de empleado. */
    boolean existsByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    /** Spec 013 / C1 — VALIDACIÓN 2: detecta un duplicado activo del concepto. */
    boolean existsByEmpleadoIdAndConceptoPlanillaIdAndActivo(
            Long empleadoId, Long conceptoPlanillaId, Integer activo);

    /** Spec 013 / C1 — duplicado activo excluyendo el registro en edición. */
    boolean existsByEmpleadoIdAndConceptoPlanillaIdAndActivoAndIdNot(
            Long empleadoId, Long conceptoPlanillaId, Integer activo, Long id);
}