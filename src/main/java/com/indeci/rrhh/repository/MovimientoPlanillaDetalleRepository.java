package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoPlanillaDetalleRepository
        extends JpaRepository<MovimientoPlanillaDetalle, Long> {

    List<MovimientoPlanillaDetalle>
    findByMovimientoPlanillaId(
            Long movimientoPlanillaId);

    void deleteByMovimientoPlanillaId(
            Long movimientoPlanillaId);
}