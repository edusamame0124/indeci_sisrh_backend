package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.EmpleadoPension;

public interface EmpleadoPensionRepository extends JpaRepository<EmpleadoPension, Long> {

    // 🔹 LISTAR
    List<EmpleadoPension> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    // 🔹 VALIDAR EXISTENCIA (UNO SOLO)
    Optional<EmpleadoPension> findFirstByEmpleadoIdAndActivo(Long empleadoId, Integer activo);
    
    
    
}