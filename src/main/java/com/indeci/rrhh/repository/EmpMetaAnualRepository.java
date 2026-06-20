package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpMetaAnual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmpMetaAnualRepository extends JpaRepository<EmpMetaAnual, Long> {

    /** Asignación vigente (no anulada) de un empleado para un año fiscal. */
    @Query("SELECT e FROM EmpMetaAnual e WHERE e.empleadoId = :empId AND e.anioFiscal = :anio AND e.estado <> 'ANULADO'")
    Optional<EmpMetaAnual> findVigenteByEmpleadoAndAnio(@Param("empId") Long empleadoId, @Param("anio") Integer anioFiscal);

    /** Asignación publicada — la que usa la planilla. */
    @Query("SELECT e FROM EmpMetaAnual e WHERE e.empleadoId = :empId AND e.anioFiscal = :anio AND e.estado = 'PUBLICADO'")
    Optional<EmpMetaAnual> findPublicadaByEmpleadoAndAnio(@Param("empId") Long empleadoId, @Param("anio") Integer anioFiscal);

    List<EmpMetaAnual> findByEmpleadoIdOrderByAnioFiscalDesc(Long empleadoId);

    List<EmpMetaAnual> findByLoteIdOrderByEmpleadoId(Long loteId);

    List<EmpMetaAnual> findByAnioFiscalAndEstadoOrderByEmpleadoId(Integer anioFiscal, String estado);

    List<EmpMetaAnual> findByAnioFiscalAndEstadoNotOrderByEmpleadoId(Integer anioFiscal, String estado);

    @Query("SELECT COUNT(e) FROM EmpMetaAnual e WHERE e.anioFiscal = :anio AND e.estado = 'PUBLICADO'")
    long countPublicadasByAnio(@Param("anio") Integer anio);

    /** Para el motor de planilla: meta publicada del empleado en el año fiscal. */
    @Query("SELECT e FROM EmpMetaAnual e WHERE e.empleadoId = :empId AND e.anioFiscal = :anio " +
           "AND e.estado = 'PUBLICADO' AND e.bloqueadoPorPlanilla IS NOT NULL")
    Optional<EmpMetaAnual> findParaPlanilla(@Param("empId") Long empleadoId, @Param("anio") Integer anioFiscal);

    /** Bloquear asignaciones publicadas de un empleado al cerrar planilla. */
    @Modifying
    @Query("UPDATE EmpMetaAnual e SET e.bloqueadoPorPlanilla = 1, e.modificadoEn = CURRENT_TIMESTAMP " +
           "WHERE e.empleadoId = :empId AND e.anioFiscal = :anio AND e.estado = 'PUBLICADO'")
    int bloquearPorPlanilla(@Param("empId") Long empleadoId, @Param("anio") Integer anioFiscal);

    /** Publicar todas las asignaciones VALIDADO de un lote. */
    @Modifying
    @Query("UPDATE EmpMetaAnual e SET e.estado = 'PUBLICADO', e.modificadoEn = CURRENT_TIMESTAMP " +
           "WHERE e.loteId = :loteId AND e.estado = 'VALIDADO'")
    int publicarPorLote(@Param("loteId") Long loteId);

    boolean existsByEmpleadoIdAndAnioFiscalAndEstadoNot(Long empleadoId, Integer anioFiscal, String estado);

    /** IDs de metas que tienen al menos una asignación vigente en el año. */
    @Query("SELECT DISTINCT e.metaPptoCatId FROM EmpMetaAnual e WHERE e.anioFiscal = :anio AND e.estado <> 'ANULADO'")
    List<Long> findMetaIdsConAsignaciones(@Param("anio") Integer anio);

    /** Cantidad de asignaciones vigentes para una meta específica en un año. */
    @Query("SELECT COUNT(e) FROM EmpMetaAnual e WHERE e.metaPptoCatId = :metaId AND e.anioFiscal = :anio AND e.estado <> 'ANULADO'")
    long countByMetaPptoCatIdAndAnioFiscal(@Param("metaId") Long metaPptoCatId, @Param("anio") Integer anioFiscal);

    /**
     * Trazabilidad paginada — usa snapshots dni/nombres/meta directamente de la tabla.
     * Object[] layout:
     *   [0] ema.id,  [1] ema.nombres,  [2] ema.dni,
     *   [3] ema.anioFiscal,  [4] cat.metaCodigo,
     *   [5] ema.centroCosto,  [6] ema.categoriaPresupuestal,
     *   [7] ema.producto,  [8] ema.actividad,  [9] ema.finalidad,
     *   [10] ema.estado,  [11] ema.origen,  [12] ema.bloqueadoPorPlanilla,
     *   [13] ema.creadoPor,  [14] ema.creadoEn,  [15] ema.observacion
     */
    @Query(
        value =
            "SELECT ema.id, ema.nombres, ema.dni, " +
            "ema.anioFiscal, cat.metaCodigo, " +
            "ema.centroCosto, ema.categoriaPresupuestal, ema.producto, ema.actividad, ema.finalidad, " +
            "ema.estado, ema.origen, ema.bloqueadoPorPlanilla, ema.creadoPor, ema.creadoEn, ema.observacion " +
            "FROM EmpMetaAnual ema " +
            "JOIN MetaPptoCat cat ON cat.id = ema.metaPptoCatId " +
            "WHERE ema.anioFiscal = :anioFiscal AND ema.estado <> 'ANULADO' " +
            "AND (:estado IS NULL OR ema.estado = :estado) " +
            "AND (:busqueda IS NULL OR LOWER(ema.nombres) LIKE :busqueda OR LOWER(ema.dni) LIKE :busqueda) " +
            "AND (:centroCosto IS NULL OR LOWER(ema.centroCosto) LIKE :centroCosto) " +
            "ORDER BY ema.nombres ASC",
        countQuery =
            "SELECT COUNT(ema.id) FROM EmpMetaAnual ema " +
            "JOIN MetaPptoCat cat ON cat.id = ema.metaPptoCatId " +
            "WHERE ema.anioFiscal = :anioFiscal AND ema.estado <> 'ANULADO' " +
            "AND (:estado IS NULL OR ema.estado = :estado) " +
            "AND (:busqueda IS NULL OR LOWER(ema.nombres) LIKE :busqueda OR LOWER(ema.dni) LIKE :busqueda) " +
            "AND (:centroCosto IS NULL OR LOWER(ema.centroCosto) LIKE :centroCosto)"
    )
    Page<Object[]> findTrazabilidad(
            @Param("anioFiscal") Integer anioFiscal,
            @Param("estado") String estado,
            @Param("busqueda") String busqueda,
            @Param("centroCosto") String centroCosto,
            Pageable pageable);
}
