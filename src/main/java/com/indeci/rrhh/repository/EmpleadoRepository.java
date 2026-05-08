package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Empleado;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {
	
	Optional<Empleado> findByPersonaId(Long personaId);



	
}