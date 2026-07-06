package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.EmpleadoPlanilla;

public interface EmpleadoPlanillaRepository extends JpaRepository<EmpleadoPlanilla, Long> {

    List<EmpleadoPlanilla> findByEmpleadoId(Long empleadoId);

    List<EmpleadoPlanilla> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    List<EmpleadoPlanilla> findByEmpleadoIdInAndActivo(List<Long> empleadoIds, Integer activo);

    @Query("SELECT p FROM EmpleadoPlanilla p WHERE p.activo = 1 " +
           "AND (:regimenLaboralId IS NULL OR p.regimenLaboralId = :regimenLaboralId) " +
           "AND (:tipoContratoId IS NULL OR p.tipoContratoId = :tipoContratoId) " +
           "AND (:condicionLaboralId IS NULL OR p.condicionLaboralId = :condicionLaboralId) " +
           "AND (:modalidadCasId IS NULL OR p.modalidadCasId = :modalidadCasId) " +
           // Fase 2 — solo vínculos cuyo rango traslapa el período generado.
           "AND (COALESCE(p.fechaInicioContrato, p.fechaInicio) IS NULL " +
           "     OR COALESCE(p.fechaInicioContrato, p.fechaInicio) <= :finPeriodo) " +
           "AND (p.fechaCese IS NULL OR p.fechaCese >= :inicioPeriodo) " +
           "AND (p.fechaFin  IS NULL OR p.fechaFin  >= :inicioPeriodo)")
    List<EmpleadoPlanilla> findEmpleadosParaGeneracion(
           @Param("regimenLaboralId") Long regimenLaboralId,
           @Param("tipoContratoId") Long tipoContratoId,
           @Param("condicionLaboralId") Long condicionLaboralId,
           @Param("modalidadCasId") Long modalidadCasId,
           @Param("inicioPeriodo") LocalDate inicioPeriodo,
           @Param("finPeriodo") LocalDate finPeriodo);

    /**
     * Fase 2 (vínculos secuenciales) — vínculo(s) activo(s) cuyo rango
     * {@code [inicio, cese/fin]} traslapa el período. Reemplaza la selección por
     * "el activo más reciente": con rotación CAS pueden coexistir dos activos
     * (uno CESADO + el nuevo) y cada período debe tomar el que le corresponde.
     * Orden determinístico: el de inicio más reciente primero.
     */
    @Query("SELECT p FROM EmpleadoPlanilla p " +
           "WHERE p.empleadoId = :empleadoId AND p.activo = 1 " +
           "AND (COALESCE(p.fechaInicioContrato, p.fechaInicio) IS NULL " +
           "     OR COALESCE(p.fechaInicioContrato, p.fechaInicio) <= :finPeriodo) " +
           "AND (p.fechaCese IS NULL OR p.fechaCese >= :inicioPeriodo) " +
           "AND (p.fechaFin  IS NULL OR p.fechaFin  >= :inicioPeriodo) " +
           "ORDER BY COALESCE(p.fechaInicioContrato, p.fechaInicio) DESC, p.id DESC")
    List<EmpleadoPlanilla> findVinculosVigentesEnPeriodo(
           @Param("empleadoId") Long empleadoId,
           @Param("inicioPeriodo") LocalDate inicioPeriodo,
           @Param("finPeriodo") LocalDate finPeriodo);

    @Query(value = """
            SELECT *
              FROM GESTIONRRHH.INDECI_EMPLEADO_PLANILLA
             WHERE EMPLEADO_ID = :empleadoId
               AND ACTIVO = :activo
             ORDER BY UPDATED_AT DESC NULLS LAST,
                      CREATED_AT DESC NULLS LAST,
                      FECHA_INICIO DESC NULLS LAST,
                      ID DESC
             FETCH FIRST 1 ROWS ONLY
            """, nativeQuery = true)
    Optional<EmpleadoPlanilla> findFirstByEmpleadoIdAndActivo(
            @Param("empleadoId") Long empleadoId,
            @Param("activo") Integer activo);
    
    List<EmpleadoPlanilla> findByActivo(Integer activo);


    /** Spec 012 / C3 (BKD-006) — paso «planilla» del flujo de empleado. */
    boolean existsByEmpleadoIdAndActivo(Long empleadoId, Integer activo);
}
