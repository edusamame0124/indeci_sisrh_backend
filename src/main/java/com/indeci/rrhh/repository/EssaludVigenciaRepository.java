package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EssaludVigencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EssaludVigenciaRepository extends JpaRepository<EssaludVigencia, Long> {

    List<EssaludVigencia> findAllByOrderByVigenciaInicioDesc();

    @Query("SELECT v FROM EssaludVigencia v WHERE v.estado NOT IN ('ANULADO') ORDER BY v.vigenciaInicio DESC")
    List<EssaludVigencia> findAllExcluyendoAnulados();

    @Query("SELECT v FROM EssaludVigencia v WHERE v.estado = :estado ORDER BY v.vigenciaInicio DESC")
    List<EssaludVigencia> findByEstadoOrderByInicioDesc(@Param("estado") String estado);

    @Query("SELECT COUNT(v) FROM EssaludVigencia v WHERE v.estado = :estado")
    long countByEstado(@Param("estado") String estado);

    /** Vigencia activa para una fecha: VIGENTE o CERRADO, dentro del rango de fechas. */
    @Query("""
        SELECT v FROM EssaludVigencia v
        WHERE v.estado NOT IN ('ANULADO', 'INACTIVO')
          AND v.vigenciaInicio <= :fecha
          AND (v.vigenciaFin IS NULL OR v.vigenciaFin >= :fecha)
        ORDER BY v.vigenciaInicio DESC
        """)
    List<EssaludVigencia> findAplicableByFecha(@Param("fecha") LocalDate fecha);

    /** Detecta solapamientos (para validar al crear/editar). */
    @Query("""
        SELECT COUNT(v) FROM EssaludVigencia v
        WHERE v.estado NOT IN ('ANULADO', 'INACTIVO')
          AND (:idExcluir IS NULL OR v.id <> :idExcluir)
          AND v.vigenciaInicio <= COALESCE(:fin, v.vigenciaInicio)
          AND (v.vigenciaFin IS NULL OR v.vigenciaFin >= :ini)
        """)
    long countSolapamiento(
            @Param("ini") LocalDate ini,
            @Param("fin") LocalDate fin,
            @Param("idExcluir") Long idExcluir);
}
