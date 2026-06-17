package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.Reconocimiento;

public interface ReconocimientoRepository
extends JpaRepository<Reconocimiento, Long> {

List<Reconocimiento>
findByEmpleadoIdAndActivoOrderByFechaReconocimientoDesc(
    Long empleadoId,
    Integer activo);
}