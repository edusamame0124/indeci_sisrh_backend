package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.OnpParametroVigencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OnpParametroVigenciaRepository extends JpaRepository<OnpParametroVigencia, Long> {

    /** Listado general: excluye ANULADO por defecto (tabla principal). */
    @Query("SELECT v FROM OnpParametroVigencia v " +
           "WHERE v.estado <> 'ANULADO' ORDER BY v.periodoInicio DESC")
    List<OnpParametroVigencia> findAllExcluyendoAnulados();

    /** Listado con filtro de estado; excluye ANULADO salvo petición explícita. */
    @Query("SELECT v FROM OnpParametroVigencia v " +
           "WHERE (:estado IS NULL OR v.estado = :estado) " +
           "AND (:incluirAnulados = TRUE OR v.estado <> 'ANULADO') " +
           "ORDER BY v.periodoInicio DESC")
    List<OnpParametroVigencia> findByEstado(
            @Param("estado") String estado,
            @Param("incluirAnulados") boolean incluirAnulados);

    /** Historial/auditoría: incluye ANULADO. */
    List<OnpParametroVigencia> findAllByOrderByPeriodoInicioDesc();

    long countByEstado(String estado);

    /** B1 — Bloquea en masa las vigencias usadas en un período cerrado. */
    @Modifying
    @Query("UPDATE OnpParametroVigencia v SET v.bloqueadoPorPlanilla = 1 WHERE v.id IN :ids")
    void bloquearByIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT v FROM OnpParametroVigencia v " +
           "WHERE v.estado = 'VIGENTE' " +
           "AND v.periodoInicio <= :periodo " +
           "AND (v.periodoFin IS NULL OR v.periodoFin >= :periodo) " +
           "ORDER BY v.periodoInicio DESC")
    Optional<OnpParametroVigencia> findVigenteByPeriodo(@Param("periodo") String periodo);

    /** Resolver UI — incluye VIGENTE y PROGRAMADO (excluye ANULADO e INACTIVO). */
    @Query("SELECT v FROM OnpParametroVigencia v " +
           "WHERE v.estado NOT IN ('ANULADO', 'INACTIVO') " +
           "AND v.periodoInicio <= :periodo " +
           "AND (v.periodoFin IS NULL OR v.periodoFin >= :periodo) " +
           "ORDER BY v.periodoInicio DESC")
    Optional<OnpParametroVigencia> findAplicableByPeriodo(@Param("periodo") String periodo);

    @Query("SELECT COUNT(v) FROM OnpParametroVigencia v " +
           "WHERE v.estado IN ('VIGENTE','PROGRAMADO') " +
           "AND (:idExcluir IS NULL OR v.id <> :idExcluir) " +
           "AND v.periodoInicio <= COALESCE(:periodoFin, '999999') " +
           "AND (v.periodoFin IS NULL OR v.periodoFin >= :periodoInicio)")
    long countSolapamiento(
            @Param("periodoInicio") String periodoInicio,
            @Param("periodoFin") String periodoFin,
            @Param("idExcluir") Long idExcluir);
}
