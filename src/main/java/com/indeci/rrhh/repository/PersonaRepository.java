package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Persona;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, Long> {
}