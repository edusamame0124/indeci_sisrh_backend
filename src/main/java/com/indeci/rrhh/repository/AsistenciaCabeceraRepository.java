package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AsistenciaCabecera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AsistenciaCabeceraRepository
        extends JpaRepository<AsistenciaCabecera, Long> {

    Optional<AsistenciaCabecera>
    findByEmpleadoIdAndPeriodoAndActivo(
            Long empleadoId,
            String periodo,
            Integer activo);

    List<AsistenciaCabecera>
    findByPeriodoAndActivo(
            String periodo,
            Integer activo);

    List<AsistenciaCabecera>
    findByImportacionIdAndActivo(
            Long importacionId,
            Integer activo);
}
