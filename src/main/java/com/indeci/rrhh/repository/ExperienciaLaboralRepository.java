package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.ExperienciaLaboral;

public interface ExperienciaLaboralRepository
extends JpaRepository<ExperienciaLaboral, Long> {

    List<ExperienciaLaboral>
    findByEmpleadoIdAndActivoOrderByFechaFinDesc(
            Long empleadoId,
            Integer activo);
}