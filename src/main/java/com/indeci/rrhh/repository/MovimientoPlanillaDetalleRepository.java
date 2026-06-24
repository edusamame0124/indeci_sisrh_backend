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

    /**
     * SPEC_CONCEPTOS_PLANILLA P1 (§8/D5) — ¿el concepto se usó en alguna planilla
     * CERRADA/APROBADA? Cruza el detalle con su movimiento y el período de planilla
     * (vía {@code MovimientoPlanilla.periodo} = {@code PeriodoPlanilla.periodo}).
     * Un período CERRADO o APROBADO es inmutable; un concepto usado allí no se edita
     * (se crea nueva versión vigente hacia adelante).
     */
    @Query("SELECT COUNT(d) FROM MovimientoPlanillaDetalle d, "
            + "MovimientoPlanilla m, PeriodoPlanilla p "
            + "WHERE d.conceptoPlanillaId = :conceptoId "
            + "AND d.movimientoPlanillaId = m.id "
            + "AND m.periodo = p.periodo "
            + "AND p.estado IN ('CERRADO', 'APROBADO')")
    long countEnPlanillaCerrada(@Param("conceptoId") Long conceptoId);
}