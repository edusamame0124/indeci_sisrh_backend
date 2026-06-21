package com.indeci.rrhh.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.SubsidioCaso;

public interface SubsidioCasoRepository extends JpaRepository<SubsidioCaso, Long> {

    Optional<SubsidioCaso> findByIdAndActivo(Long id, Integer activo);

    long countByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    @Query("""
            SELECT c FROM SubsidioCaso c
             WHERE c.activo = 1
               AND (:periodo IS NULL OR EXISTS (
                    SELECT 1 FROM SubsidioTramo t
                     WHERE t.casoId = c.id AND t.activo = 1 AND t.esVigente = 'S'
                       AND t.periodo = :periodo))
               AND (:tipo IS NULL OR c.tipoCaso = :tipo)
               AND (:estado IS NULL OR c.estado = :estado)
               AND (:empleadoId IS NULL OR c.empleadoId = :empleadoId)
            """)
    Page<SubsidioCaso> findBandeja(
            @Param("periodo") String periodo,
            @Param("tipo") String tipo,
            @Param("estado") String estado,
            @Param("empleadoId") Long empleadoId,
            Pageable pageable);

    @Query("""
            SELECT c FROM SubsidioCaso c
             WHERE c.activo = 1
               AND (:periodo IS NULL OR EXISTS (
                    SELECT 1 FROM SubsidioTramo t
                     WHERE t.casoId = c.id AND t.activo = 1 AND t.esVigente = 'S'
                       AND t.periodo = :periodo))
               AND (:tipo IS NULL OR c.tipoCaso = :tipo)
               AND (:estado IS NULL OR c.estado = :estado)
               AND (:empleadoId IS NULL OR c.empleadoId = :empleadoId)
               AND (:dni IS NULL OR c.empleadoId IN (
                    SELECT e.id FROM Empleado e JOIN Persona p ON p.id = e.personaId
                     WHERE p.dni LIKE CONCAT('%', :dni, '%')))
            """)
    Page<SubsidioCaso> findBandejaConDni(
            @Param("periodo") String periodo,
            @Param("tipo") String tipo,
            @Param("estado") String estado,
            @Param("empleadoId") Long empleadoId,
            @Param("dni") String dni,
            Pageable pageable);
}
