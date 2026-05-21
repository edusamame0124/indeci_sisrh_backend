package com.indeci.rrhh.repository;

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
}
