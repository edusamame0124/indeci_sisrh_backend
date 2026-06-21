package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.SubsidioTramo;

public interface SubsidioTramoRepository extends JpaRepository<SubsidioTramo, Long> {

    List<SubsidioTramo> findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(
            Long casoId, Integer activo, String esVigente);

    Optional<SubsidioTramo> findByIdAndActivo(Long id, Integer activo);

    @Modifying
    @Query("UPDATE SubsidioTramo t SET t.esVigente = 'N' WHERE t.casoId = :casoId AND t.periodo = :periodo")
    void desactivarVigentesPorCasoYPeriodo(
            @Param("casoId") Long casoId, @Param("periodo") String periodo);

    @Query("""
            SELECT t FROM SubsidioTramo t
             JOIN SubsidioCaso c ON c.id = t.casoId
             WHERE c.empleadoId = :empleadoId
               AND t.periodo = :periodo
               AND t.activo = 1
               AND t.esVigente = 'S'
               AND c.activo = 1
            """)
    List<SubsidioTramo> findVigentesPorEmpleadoYPeriodo(
            @Param("empleadoId") Long empleadoId,
            @Param("periodo") String periodo);
}
