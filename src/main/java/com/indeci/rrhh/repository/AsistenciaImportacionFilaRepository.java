package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AsistenciaImportacionFilaRepository
        extends JpaRepository<AsistenciaImportacionFila, Long> {

    List<AsistenciaImportacionFila> findByImportacionIdOrderByNumeroFila(Long importacionId);

    void deleteByImportacionId(Long importacionId);

    /**
     * F2 — Detalle paginado server-side con filtros (req 11/12, P8).
     * Filtros opcionales: dni (contiene), nombre (contiene en CSV o sistema),
     * estado (estadoFila exacto), soloErrores (ERROR u OBSERVADA).
     */
    @Query("""
            SELECT f FROM AsistenciaImportacionFila f
             WHERE f.importacionId = :importacionId
               AND (:dni IS NULL OR f.dni LIKE CONCAT('%', :dni, '%'))
               AND (:nombre IS NULL
                    OR UPPER(f.nombreCsv) LIKE UPPER(CONCAT('%', :nombre, '%'))
                    OR UPPER(f.nombreSistema) LIKE UPPER(CONCAT('%', :nombre, '%')))
               AND (:estado IS NULL OR f.estadoFila = :estado)
               AND (:soloErrores = false OR f.estadoFila IN ('ERROR', 'OBSERVADA'))
             ORDER BY f.numeroFila
            """)
    Page<AsistenciaImportacionFila> buscarDetalle(
            @Param("importacionId") Long importacionId,
            @Param("dni") String dni,
            @Param("nombre") String nombre,
            @Param("estado") String estado,
            @Param("soloErrores") boolean soloErrores,
            Pageable pageable);

    /**
     * F2 (COEN) — Nombres del marcador que quedaron SIN MAPEAR en una importación
     * (identidad no resuelta por alias), agrupados con la cantidad de días afectados.
     * El validador marca esas filas con mensaje "SIN_MAPEO: ...".
     * Columnas: [0]=nombreCsv [1]=cantidad de días.
     */
    @Query("""
            SELECT f.nombreCsv, COUNT(f)
              FROM AsistenciaImportacionFila f
             WHERE f.importacionId = :importacionId
               AND f.mensajeValidacion LIKE 'SIN_MAPEO%'
             GROUP BY f.nombreCsv
             ORDER BY f.nombreCsv
            """)
    List<Object[]> resumenSinMapeo(@Param("importacionId") Long importacionId);
}
