package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.Capacitacion;

@Repository
public interface CapacitacionRepository
        extends JpaRepository<Capacitacion,Long>{

    List<Capacitacion>
    findByEmpleadoIdAndActivoOrderByFechaFinDesc(
            Long empleadoId,
            Integer activo);

}