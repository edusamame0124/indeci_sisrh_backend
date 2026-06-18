package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.TeletrabajoReporte;

@Repository
public interface TeletrabajoReporteRepository
        extends JpaRepository<
                TeletrabajoReporte,
                Long> {

    List<TeletrabajoReporte>
    findByActivoOrderByIdDesc(
            Integer activo);

    List<TeletrabajoReporte>
    findByEmpleadoIdAndActivo(
            Long empleadoId,
            Integer activo);
}