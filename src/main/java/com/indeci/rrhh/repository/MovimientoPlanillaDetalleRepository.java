package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MovimientoPlanillaDetalleRepository
        extends JpaRepository<MovimientoPlanillaDetalle, Long> {

    List<MovimientoPlanillaDetalle>
    findByMovimientoPlanillaId(Long movimientoPlanillaId);

    void deleteByMovimientoPlanillaId(Long movimientoPlanillaId);

    /** B1 export — todos los detalles de los movimientos de un período (batch). */
    @Query("SELECT d FROM MovimientoPlanillaDetalle d WHERE d.movimientoPlanillaId IN :ids")
    List<MovimientoPlanillaDetalle> findByMovimientoPlanillaIdIn(
            @Param("ids") Collection<Long> ids);
}