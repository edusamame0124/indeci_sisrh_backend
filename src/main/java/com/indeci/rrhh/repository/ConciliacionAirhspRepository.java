package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.ConciliacionAirhsp;

public interface ConciliacionAirhspRepository
        extends JpaRepository<ConciliacionAirhsp, Long> {

    Optional<ConciliacionAirhsp> findByMovimientoPlanillaIdAndEmpleadoId(
            Long movimientoPlanillaId, Long empleadoId);

    List<ConciliacionAirhsp> findByPeriodoPlanillaIdAndEstado(
            Long periodoPlanillaId, String estado);

    List<ConciliacionAirhsp> findByPeriodoPlanillaId(Long periodoPlanillaId);

    /** Limpieza al regenerar la planilla (evita FK colgante al borrar el movimiento). */
    void deleteByMovimientoPlanillaId(Long movimientoPlanillaId);
}
