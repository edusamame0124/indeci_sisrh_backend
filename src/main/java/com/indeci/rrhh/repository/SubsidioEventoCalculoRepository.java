package com.indeci.rrhh.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.SubsidioEventoCalculo;

public interface SubsidioEventoCalculoRepository
        extends JpaRepository<SubsidioEventoCalculo, Long> {

    @Modifying
    @Query("""
            UPDATE SubsidioEventoCalculo c
               SET c.activo = 0
             WHERE c.empleadoEventoId = :empleadoEventoId
               AND c.activo = 1
            """)
    int desactivarVigentesPorEvento(@Param("empleadoEventoId") Long empleadoEventoId);
}
