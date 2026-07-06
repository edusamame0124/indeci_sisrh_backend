package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.ReglaBeneficioCas;

/** Track B F4 — Reglas versionadas de beneficio CAS (Ley 32563). */
public interface ReglaBeneficioCasRepository extends JpaRepository<ReglaBeneficioCas, Long> {

    /**
     * Regla(s) ACTIVA(s) de gratificación por porcentaje de remuneración para el
     * régimen y mes dados, vigentes a la fecha. Ordenadas por vigencia más
     * reciente primero (la primera es la aplicable). Solo {@code PCT_REMUNERACION}
     * (la legacy de monto fijo está INACTIVA y no se aplica en nuevos cálculos).
     */
    @Query("SELECT r FROM ReglaBeneficioCas r "
            + "WHERE r.regimen = :regimen AND r.mesAplica = :mes "
            + "AND r.estado = 'ACTIVO' AND r.montoTipo = 'PCT_REMUNERACION' "
            + "AND r.vigenciaDesde <= :fecha "
            + "AND (r.vigenciaHasta IS NULL OR r.vigenciaHasta >= :fecha) "
            + "ORDER BY r.vigenciaDesde DESC, r.id DESC")
    List<ReglaBeneficioCas> findGratificacionesVigentes(
            @Param("regimen") String regimen,
            @Param("mes") Integer mes,
            @Param("fecha") LocalDate fecha);
}
