package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.SubsidioBaseDetalle;

public interface SubsidioBaseDetalleRepository extends JpaRepository<SubsidioBaseDetalle, Long> {

    List<SubsidioBaseDetalle> findByBaseHistoricaIdOrderByPeriodoAsc(Long baseHistoricaId);
}
