package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.TeletrabajoReporteDet;

@Repository
public interface TeletrabajoReporteDetRepository
        extends JpaRepository<
                TeletrabajoReporteDet,
                Long> {

    List<TeletrabajoReporteDet>
    findByReporteIdAndActivoOrderByNroOrdenAsc(
            Long reporteId,
            Integer activo);
    
    Integer countByReporteId(
            Long reporteId);
}