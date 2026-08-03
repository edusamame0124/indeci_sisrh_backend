package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.Oficina;

public interface EmpleadoPuestoRepository extends JpaRepository<EmpleadoPuesto, Long> {

    List<EmpleadoPuesto> findByEmpleadoIdOrderByFechaInicioDesc(Long empleadoId);

    Optional<EmpleadoPuesto> findFirstByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    /** P0 Planilla CAS Consolidada — puestos activos en batch (sin N+1). */
    List<EmpleadoPuesto> findByEmpleadoIdInAndActivo(List<Long> empleadoIds, Integer activo);

    /** Spec 012 / C3 (BKD-006) — paso «puesto» del flujo de empleado. */
    boolean existsByEmpleadoId(Long empleadoId);
    
    
    List<EmpleadoPuesto>
    findByJefeIdAndActivo(
            Long jefeId,
            Integer activo);
    
    List<Oficina> findBySedeId(Long sedeId);

    /**
     * Reporte de asistencia consolidado — puesto (oficina/dependencia) vigente de cada
     * empleado a una fecha de corte (histórico, {@code INDECI_EMPLEADO_PUESTO_HIST}), en
     * batch para evitar N+1. Si un empleado tiene más de un registro solapado, el llamador
     * toma el primero (dato de calidad a corregir en Organización, no en el reporte).
     */
    @Query("select ep from EmpleadoPuesto ep "
            + "where ep.empleadoId in :empleadoIds "
            + "and ep.fechaInicio <= :fecha "
            + "and (ep.fechaFin is null or ep.fechaFin >= :fecha) "
            + "order by ep.fechaInicio desc")
    List<EmpleadoPuesto> findVigentesEnFecha(
            @Param("empleadoIds") List<Long> empleadoIds,
            @Param("fecha") LocalDate fecha);
}