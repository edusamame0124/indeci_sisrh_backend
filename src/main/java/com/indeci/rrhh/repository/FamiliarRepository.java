package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.Familiar;

public interface FamiliarRepository
extends JpaRepository<Familiar, Long> {

List<Familiar>
findByEmpleadoIdAndActivo(
    Long empleadoId,
    Integer activo);
}