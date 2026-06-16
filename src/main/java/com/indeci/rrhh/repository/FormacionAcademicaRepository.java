package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.FormacionAcademica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormacionAcademicaRepository
        extends JpaRepository<FormacionAcademica, Long> {

    List<FormacionAcademica>
    findByEmpleadoIdAndActivoOrderByFechaFinDesc(
            Long empleadoId,
            Integer activo);
}