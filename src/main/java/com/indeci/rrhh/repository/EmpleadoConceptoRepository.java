package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpleadoConcepto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpleadoConceptoRepository
        extends JpaRepository<EmpleadoConcepto, Long> {

    List<EmpleadoConcepto>
    findByEmpleadoIdAndActivo(
            Long empleadoId,
            Integer activo);
}