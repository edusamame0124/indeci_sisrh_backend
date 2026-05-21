package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.Suspension;

public interface SuspensionRepository
        extends JpaRepository<Suspension, Long> {

    /** Suspensiones de un empleado por estado (pantalla de mantenimiento). */
    List<Suspension> findByEmpleadoIdAndEstadoOrderByFechaInicio(
            Long empleadoId, String estado);

    /**
     * Suspensiones activas que solapan el rango [desde, hasta] del período.
     * Solapamiento: FECHA_INICIO <= hasta AND FECHA_FIN >= desde.
     * Base para generar el .snl del mes.
     */
    List<Suspension> findByEstadoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            String estado, LocalDate hasta, LocalDate desde);
}
