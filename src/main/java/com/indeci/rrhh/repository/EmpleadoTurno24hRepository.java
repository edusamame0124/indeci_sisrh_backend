package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpleadoTurno24h;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpleadoTurno24hRepository extends JpaRepository<EmpleadoTurno24h, Long> {

    List<EmpleadoTurno24h> findByEmpleadoIdAndActivoOrderByFechaInicioDesc(
            Long empleadoId, Integer activo);

    /** Batch por lote de empleados — evita N+1 al reconciliar un import completo. */
    List<EmpleadoTurno24h> findByEmpleadoIdInAndActivo(List<Long> empleadoIds, Integer activo);

    /** Lista completa de turnos 24h activos — la usa el backfill histórico. */
    List<EmpleadoTurno24h> findByActivo(Integer activo);
}
