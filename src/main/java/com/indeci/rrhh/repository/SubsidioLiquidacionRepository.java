package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.SubsidioLiquidacion;

public interface SubsidioLiquidacionRepository extends JpaRepository<SubsidioLiquidacion, Long> {

    Optional<SubsidioLiquidacion> findByTramoIdAndEsVigente(Long tramoId, String esVigente);

    List<SubsidioLiquidacion> findByTramoIdOrderByVersionLiqDesc(Long tramoId);

    @Modifying
    @Query("UPDATE SubsidioLiquidacion l SET l.esVigente = 'N' WHERE l.tramoId = :tramoId")
    void desactivarVigentesPorTramo(@Param("tramoId") Long tramoId);

    @Query("""
            SELECT l FROM SubsidioLiquidacion l
             JOIN SubsidioTramo t ON t.id = l.tramoId
             JOIN SubsidioCaso c ON c.id = t.casoId
             WHERE c.empleadoId = :empleadoId
               AND t.periodo = :periodo
               AND l.esVigente = 'S'
               AND l.estado = :estado
               AND t.activo = 1
               AND c.activo = 1
            """)
    List<SubsidioLiquidacion> findVigentesPorEmpleadoPeriodoEstado(
            @Param("empleadoId") Long empleadoId,
            @Param("periodo") String periodo,
            @Param("estado") String estado);
}
