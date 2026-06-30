package com.indeci.rrhh.repository;

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
           "AND (:modalidadCasId IS NULL OR p.modalidadCasId = :modalidadCasId)")
    List<EmpleadoPlanilla> findEmpleadosParaGeneracion(
           @Param("regimenLaboralId") Long regimenLaboralId,
           @Param("tipoContratoId") Long tipoContratoId,
           @Param("condicionLaboralId") Long condicionLaboralId,
           @Param("modalidadCasId") Long modalidadCasId);

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
