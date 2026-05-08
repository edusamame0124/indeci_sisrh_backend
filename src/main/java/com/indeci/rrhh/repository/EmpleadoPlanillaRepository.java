package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.EmpleadoPlanilla;

public interface EmpleadoPlanillaRepository extends JpaRepository<EmpleadoPlanilla, Long> {

    List<EmpleadoPlanilla> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    Optional<EmpleadoPlanilla> findFirstByEmpleadoIdAndActivo(Long empleadoId, Integer activo);
    
    List<EmpleadoPlanilla> findByActivo(Integer activo);
}