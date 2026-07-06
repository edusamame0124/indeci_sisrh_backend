package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.EmpleadoRemuneracionHist;

/** Acceso a INDECI_EMPLEADO_REMUNERACION_HIST (F2). */
public interface EmpleadoRemuneracionHistRepository
        extends JpaRepository<EmpleadoRemuneracionHist, Long> {

    /** Remuneración vigente y APROBADA a una fecha (la más reciente que aplica). */
    @Query("""
            SELECT h FROM EmpleadoRemuneracionHist h
             WHERE h.empleadoPlanillaId = :empleadoPlanillaId
               AND h.estado = 'APROBADO'
               AND h.vigenciaDesde <= :fecha
               AND (h.vigenciaHasta IS NULL OR h.vigenciaHasta >= :fecha)
             ORDER BY h.vigenciaDesde DESC
            """)
    List<EmpleadoRemuneracionHist> findVigenteAprobada(
            @Param("empleadoPlanillaId") Long empleadoPlanillaId,
            @Param("fecha") LocalDate fecha,
            Pageable pageable);

    List<EmpleadoRemuneracionHist> findByEmpleadoPlanillaIdOrderByVigenciaDesdeDesc(
            Long empleadoPlanillaId);

    /** Cuenta cambios APROBADOS que inician DENTRO del rango (para detectar el caso
     *  "cambio remunerativo dentro del período" que requiere tramos). */
    @Query("""
            SELECT COUNT(h) FROM EmpleadoRemuneracionHist h
             WHERE h.empleadoPlanillaId = :empleadoPlanillaId
               AND h.estado = 'APROBADO'
               AND h.vigenciaDesde > :inicio
               AND h.vigenciaDesde <= :fin
            """)
    long countCambiosEnRango(
            @Param("empleadoPlanillaId") Long empleadoPlanillaId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);
}
