package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.Idioma;

public interface IdiomaRepository
extends JpaRepository<Idioma, Long> {

List<Idioma>
findByEmpleadoIdAndActivoOrderByIdiomaAsc(
    Long empleadoId,
    Integer activo);
}