package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpleadoJornadaExcepcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpleadoJornadaExcepcionRepository extends JpaRepository<EmpleadoJornadaExcepcion, Long> {

    List<EmpleadoJornadaExcepcion> findByEmpleadoIdAndActivoOrderByFechaInicioDesc(
            Long empleadoId, Integer activo);

    /** Batch por lote de empleados — evita N+1 al enriquecer una página de filas (Consulta Diaria). */
    List<EmpleadoJornadaExcepcion> findByEmpleadoIdInAndActivo(List<Long> empleadoIds, Integer activo);
}
