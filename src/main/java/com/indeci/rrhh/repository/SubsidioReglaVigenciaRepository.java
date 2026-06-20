package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.SubsidioReglaVigencia;

public interface SubsidioReglaVigenciaRepository extends JpaRepository<SubsidioReglaVigencia, Long> {

    @Query("""
            SELECT r FROM SubsidioReglaVigencia r
             WHERE r.estado = 'VIGENTE'
               AND r.fechaVigIni <= :fecha
               AND (r.fechaVigFin IS NULL OR r.fechaVigFin >= :fecha)
             ORDER BY r.fechaVigIni DESC
            """)
    Optional<SubsidioReglaVigencia> findVigenteEnFecha(@Param("fecha") LocalDate fecha);
}
