package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.EmpleadoPuesto;

public interface EmpleadoPuestoRepository extends JpaRepository<EmpleadoPuesto, Long> {

    List<EmpleadoPuesto> findByEmpleadoIdOrderByFechaInicioDesc(Long empleadoId);

    Optional<EmpleadoPuesto> findFirstByEmpleadoIdAndActivo(Long empleadoId, Integer activo);
}