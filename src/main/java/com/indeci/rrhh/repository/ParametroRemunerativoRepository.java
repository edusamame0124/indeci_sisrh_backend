package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.ParametroRemunerativo;

public interface ParametroRemunerativoRepository
        extends JpaRepository<ParametroRemunerativo, Long> {

    /**
     * Busca el parámetro vigente para un (código, año, régimen). Si no encuentra
     * por régimen, debe llamarse al método {@link #findVigenteGlobal}.
     */
    @Query("""
            SELECT p
              FROM ParametroRemunerativo p
             WHERE p.codigoParametro     = :codigo
               AND p.anioFiscal          = :anio
               AND p.regimenLaboralId    = :regimenLaboralId
               AND p.activo              = 1
             ORDER BY p.fechaVigIni DESC
            """)
    Optional<ParametroRemunerativo> findVigenteByRegimen(
            @Param("codigo") String codigo,
            @Param("anio") Integer anio,
            @Param("regimenLaboralId") Long regimenLaboralId);

    /**
     * Busca el parámetro "global" (REGIMEN_LABORAL_ID = NULL) vigente.
     */
    @Query("""
            SELECT p
              FROM ParametroRemunerativo p
             WHERE p.codigoParametro     = :codigo
               AND p.anioFiscal          = :anio
               AND p.regimenLaboralId IS NULL
               AND p.activo              = 1
             ORDER BY p.fechaVigIni DESC
            """)
    Optional<ParametroRemunerativo> findVigenteGlobal(
            @Param("codigo") String codigo,
            @Param("anio") Integer anio);

    /**
     * F1.3a — Busca el parámetro vigente por régimen en una fecha de devengue
     * concreta (soporta C4 RRHH: parámetros con vigencia mensual).
     *
     * Comportamiento: FECHA_VIG_INI ≤ fecha ≤ NVL(FECHA_VIG_FIN, +∞). Si hay
     * más de una fila ACTIVO=1 que solapa la fecha, devuelve la de FECHA_VIG_INI
     * más reciente (defensa por consistencia).
     */
    @Query("""
            SELECT p
              FROM ParametroRemunerativo p
             WHERE p.codigoParametro     = :codigo
               AND p.regimenLaboralId    = :regimenLaboralId
               AND p.activo              = 1
               AND p.fechaVigIni        <= :fechaDevengue
               AND (p.fechaVigFin IS NULL OR p.fechaVigFin >= :fechaDevengue)
             ORDER BY p.fechaVigIni DESC
            """)
    Optional<ParametroRemunerativo> findVigenteByRegimenEnFecha(
            @Param("codigo") String codigo,
            @Param("regimenLaboralId") Long regimenLaboralId,
            @Param("fechaDevengue") LocalDate fechaDevengue);

    /**
     * F1.3a — Variante global (REGIMEN_LABORAL_ID = NULL) del lookup por fecha.
     */
    @Query("""
            SELECT p
              FROM ParametroRemunerativo p
             WHERE p.codigoParametro     = :codigo
               AND p.regimenLaboralId IS NULL
               AND p.activo              = 1
               AND p.fechaVigIni        <= :fechaDevengue
               AND (p.fechaVigFin IS NULL OR p.fechaVigFin >= :fechaDevengue)
             ORDER BY p.fechaVigIni DESC
            """)
    Optional<ParametroRemunerativo> findVigenteGlobalEnFecha(
            @Param("codigo") String codigo,
            @Param("fechaDevengue") LocalDate fechaDevengue);
}
