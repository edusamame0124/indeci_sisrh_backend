package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.PlanillaLote;
import com.indeci.rrhh.dto.PlanillaLoteDashboardDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanillaLoteRepository extends JpaRepository<PlanillaLote, Long> {

    @Query("SELECT MAX(p.correlativo) FROM PlanillaLote p WHERE p.periodo = :periodo " +
           "AND p.regimenLaboralCodigo = :regimen " +
           "AND p.tipoPlanilla = :tipoPlanilla")
    Integer findMaxCorrelativo(@Param("periodo") String periodo, 
                               @Param("regimen") String regimen, 
                               @Param("tipoPlanilla") String tipoPlanilla);

    @Query("SELECT new com.indeci.rrhh.dto.PlanillaLoteDashboardDto(" +
           "l.id, l.periodo, l.regimenLaboralCodigo, l.tipoPlanilla, l.correlativo, l.estado, l.creadoEn, " +
           "COUNT(m.id), COALESCE(SUM(m.netoPagar), 0.0), l.motivo) " +
           "FROM PlanillaLote l LEFT JOIN MovimientoPlanilla m ON m.loteId = l.id " +
           "WHERE l.periodo = :periodo AND (:regimen IS NULL OR l.regimenLaboralCodigo = :regimen) " +
           "GROUP BY l.id, l.periodo, l.regimenLaboralCodigo, l.tipoPlanilla, l.correlativo, l.estado, l.creadoEn, l.motivo " +
           "ORDER BY l.creadoEn DESC")
    List<PlanillaLoteDashboardDto> findLotesDashboard(@Param("periodo") String periodo, 
                                                      @Param("regimen") String regimen);
                                                      
    Optional<PlanillaLote> findByPeriodoAndRegimenLaboralCodigoAndTipoPlanillaAndCorrelativo(
            String periodo, String regimen, String tipoPlanilla, Integer correlativo);
}
