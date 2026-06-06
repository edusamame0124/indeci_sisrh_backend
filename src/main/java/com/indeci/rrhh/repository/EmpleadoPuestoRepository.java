package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.Oficina;

public interface EmpleadoPuestoRepository extends JpaRepository<EmpleadoPuesto, Long> {

    List<EmpleadoPuesto> findByEmpleadoIdOrderByFechaInicioDesc(Long empleadoId);

    Optional<EmpleadoPuesto> findFirstByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    /** Spec 012 / C3 (BKD-006) — paso «puesto» del flujo de empleado. */
    boolean existsByEmpleadoId(Long empleadoId);
    
    
    List<EmpleadoPuesto>
    findByJefeIdAndActivo(
            Long jefeId,
            Integer activo);
    
    List<Oficina> findBySedeId(Long sedeId);
}