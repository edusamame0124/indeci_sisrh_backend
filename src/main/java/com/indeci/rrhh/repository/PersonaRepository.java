package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Persona;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, Long> {
	
	 // 🔥 VALIDACIONES
    boolean existsByDni(String dni);

    boolean existsByEmail(String email);
    // 🔥 OPCIONAL (útil después)
    Optional<Persona> findByDni(String dni);
}