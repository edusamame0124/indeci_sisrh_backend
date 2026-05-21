package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.DescuentoVoluntario;

public interface DescuentoVoluntarioRepository
        extends JpaRepository<DescuentoVoluntario, Long> {

    List<DescuentoVoluntario> findByEmpleadoIdAndEstado(
            Long empleadoId, String estado);

    List<DescuentoVoluntario> findByEmpleadoId(Long empleadoId);
}
