package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.PeriodoPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PeriodoPlanillaRepository
        extends JpaRepository<PeriodoPlanilla, Long> {

    List<PeriodoPlanilla> findByActivo(Integer activo);

    Optional<PeriodoPlanilla>
    findByPeriodoAndActivo(
            String periodo,
            Integer activo);

    /**
     * Validación conservadora para anulación de vigencias previsionales.
     * Cuenta períodos de planilla CERRADOS o APROBADOS cuyo período YYYYMM
     * cae dentro del rango de vigencia [periodoInicio, periodoFin].
     * Si periodoFin es NULL (vigencia abierta), se verifica solo >= periodoInicio.
     */
    @Query("SELECT COUNT(p) FROM PeriodoPlanilla p " +
           "WHERE p.estado IN ('CERRADO','APROBADO') " +
           "AND p.activo = 1 " +
           "AND p.periodo >= :periodoInicio " +
           "AND (:periodoFin IS NULL OR p.periodo <= :periodoFin)")
    long countPlanillasCerradasEnRango(
            @Param("periodoInicio") String periodoInicio,
            @Param("periodoFin") String periodoFin);
}