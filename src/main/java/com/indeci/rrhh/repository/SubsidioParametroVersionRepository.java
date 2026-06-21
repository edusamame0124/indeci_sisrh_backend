package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.SubsidioParametroVersion;

public interface SubsidioParametroVersionRepository
        extends JpaRepository<SubsidioParametroVersion, Long> {

    @Query("""
            SELECT v FROM SubsidioParametroVersion v
             WHERE v.parametroId = :parametroId
               AND v.estado = 'VIGENTE'
               AND v.fechaVigIni <= :fecha
               AND (v.fechaVigFin IS NULL OR v.fechaVigFin >= :fecha)
             ORDER BY v.fechaVigIni DESC
            """)
    Optional<SubsidioParametroVersion> findVigente(
            @Param("parametroId") Long parametroId,
            @Param("fecha") LocalDate fecha);

    List<SubsidioParametroVersion> findByParametroIdAndEstado(Long parametroId, String estado);
}
