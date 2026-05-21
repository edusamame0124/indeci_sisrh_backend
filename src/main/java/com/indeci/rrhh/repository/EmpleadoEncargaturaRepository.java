package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.EmpleadoEncargatura;

public interface EmpleadoEncargaturaRepository
        extends JpaRepository<EmpleadoEncargatura, Long> {

    List<EmpleadoEncargatura> findByEmpleadoTitularIdAndEstado(
            Long empleadoTitularId, String estado);

    List<EmpleadoEncargatura> findByEmpleadoEncargIdAndEstado(
            Long empleadoEncargId, String estado);
}
