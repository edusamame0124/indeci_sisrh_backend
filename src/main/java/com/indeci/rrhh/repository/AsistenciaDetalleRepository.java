package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AsistenciaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsistenciaDetalleRepository
        extends JpaRepository<AsistenciaDetalle, Long> {

    List<AsistenciaDetalle> findByCabeceraIdOrderByDia(Long cabeceraId);

    void deleteByCabeceraId(Long cabeceraId);
}
