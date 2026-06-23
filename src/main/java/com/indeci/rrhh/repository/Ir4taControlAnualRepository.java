package com.indeci.rrhh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.Ir4taControlAnual;

/**
 * V010_94 — Repositorio del control anual de suspensión IR4ta por trabajador.
 * Una fila por (empleadoId, anioFiscal).
 */
public interface Ir4taControlAnualRepository extends JpaRepository<Ir4taControlAnual, Long> {

    Optional<Ir4taControlAnual> findByEmpleadoIdAndAnioFiscal(Long empleadoId, Integer anioFiscal);
}
