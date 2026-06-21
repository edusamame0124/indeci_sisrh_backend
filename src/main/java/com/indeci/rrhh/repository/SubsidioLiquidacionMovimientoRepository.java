package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.SubsidioLiquidacionMovimiento;

public interface SubsidioLiquidacionMovimientoRepository
        extends JpaRepository<SubsidioLiquidacionMovimiento, Long> {

    List<SubsidioLiquidacionMovimiento> findByLiquidacionId(Long liquidacionId);

    List<SubsidioLiquidacionMovimiento> findByMovimientoPlanillaId(Long movimientoPlanillaId);
}
