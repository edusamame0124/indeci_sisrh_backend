package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Ir4taConfigAnual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface Ir4taConfigAnualRepository extends JpaRepository<Ir4taConfigAnual, Long> {

    List<Ir4taConfigAnual> findAllByOrderByAnioFiscalDesc();

    @Query("SELECT c FROM Ir4taConfigAnual c WHERE c.estado NOT IN ('ANULADO') ORDER BY c.anioFiscal DESC")
    List<Ir4taConfigAnual> findAllExcluyendoAnulados();

    @Query("SELECT c FROM Ir4taConfigAnual c WHERE c.estado = :estado ORDER BY c.anioFiscal DESC")
    List<Ir4taConfigAnual> findByEstadoOrderByAnioDesc(@Param("estado") String estado);

    @Query("SELECT COUNT(c) FROM Ir4taConfigAnual c WHERE c.estado = :estado")
    long countByEstado(@Param("estado") String estado);

    /** Configuración aplicable para una fecha de devengue: VIGENTE o CERRADO dentro del rango. */
    @Query("""
        SELECT c FROM Ir4taConfigAnual c
        WHERE c.estado NOT IN ('ANULADO', 'BORRADOR')
          AND c.vigenciaInicio <= :fecha
          AND (c.vigenciaFin IS NULL OR c.vigenciaFin >= :fecha)
        ORDER BY c.vigenciaInicio DESC
        """)
    List<Ir4taConfigAnual> findAplicableByFecha(@Param("fecha") LocalDate fecha);

    /** Para el motor: resolución rápida por año fiscal. */
    @Query("""
        SELECT c FROM Ir4taConfigAnual c
        WHERE c.anioFiscal = :anio
          AND c.estado NOT IN ('ANULADO', 'BORRADOR')
        ORDER BY c.vigenciaInicio DESC
        """)
    List<Ir4taConfigAnual> findByAnioFiscalActivo(@Param("anio") Integer anio);

    /** Detecta solapamientos de fechas (excluyendo el registro en edición). */
    @Query("""
        SELECT COUNT(c) FROM Ir4taConfigAnual c
        WHERE c.estado NOT IN ('ANULADO')
          AND (:idExcluir IS NULL OR c.id <> :idExcluir)
          AND c.vigenciaInicio <= COALESCE(:fin, c.vigenciaInicio)
          AND (c.vigenciaFin IS NULL OR c.vigenciaFin >= :ini)
        """)
    long countSolapamiento(
            @Param("ini") LocalDate ini,
            @Param("fin") LocalDate fin,
            @Param("idExcluir") Long idExcluir);

    Optional<Ir4taConfigAnual> findFirstByAnioFiscalAndEstadoOrderByVigenciaInicioDesc(
            Integer anioFiscal, String estado);
}
