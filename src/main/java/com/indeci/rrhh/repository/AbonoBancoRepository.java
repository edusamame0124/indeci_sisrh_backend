package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.AbonoBanco;

public interface AbonoBancoRepository
        extends JpaRepository<AbonoBanco, Long> {

    List<AbonoBanco> findByMovimientoPlanillaId(Long movimientoPlanillaId);

    List<AbonoBanco> findByMovimientoPlanillaIdAndBanco(
            Long movimientoPlanillaId, String banco);

    Optional<AbonoBanco> findByMovimientoPlanillaIdAndEmpleadoId(
            Long movimientoPlanillaId, Long empleadoId);

    /** Limpieza al regenerar la planilla (evita FK colgante al borrar el movimiento). */
    void deleteByMovimientoPlanillaId(Long movimientoPlanillaId);
}
