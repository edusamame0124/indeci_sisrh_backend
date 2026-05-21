package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.VacacionSaldo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VacacionSaldoRepository extends JpaRepository<VacacionSaldo, Long> {

    List<VacacionSaldo> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    Optional<VacacionSaldo> findByEmpleadoIdAndAnioAndActivo(
            Long empleadoId, Integer anio, Integer activo);
}
