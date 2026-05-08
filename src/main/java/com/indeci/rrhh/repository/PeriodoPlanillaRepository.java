package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.PeriodoPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodoPlanillaRepository
        extends JpaRepository<PeriodoPlanilla, Long> {

    List<PeriodoPlanilla> findByActivo(Integer activo);

    Optional<PeriodoPlanilla>
    findByPeriodoAndActivo(
            String periodo,
            Integer activo);
}