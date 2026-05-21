package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MovimientoPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;

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
}