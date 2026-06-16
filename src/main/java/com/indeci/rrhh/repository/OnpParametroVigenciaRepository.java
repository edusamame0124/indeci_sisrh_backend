package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.OnpParametroVigencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OnpParametroVigenciaRepository extends JpaRepository<OnpParametroVigencia, Long> {

    List<OnpParametroVigencia> findAllByOrderByPeriodoInicioDesc();

    @Query("SELECT v FROM OnpParametroVigencia v " +
           "WHERE v.estado = 'VIGENTE' " +
           "AND v.periodoInicio <= :periodo " +
           "AND (v.periodoFin IS NULL OR v.periodoFin >= :periodo) " +
           "ORDER BY v.periodoInicio DESC")
    Optional<OnpParametroVigencia> findVigenteByPeriodo(@Param("periodo") String periodo);

    @Query("SELECT COUNT(v) FROM OnpParametroVigencia v " +
           "WHERE v.estado IN ('VIGENTE','PROGRAMADO') " +
           "AND (:idExcluir IS NULL OR v.id <> :idExcluir) " +
           "AND v.periodoInicio <= COALESCE(:periodoFin, '999999') " +
           "AND (v.periodoFin IS NULL OR v.periodoFin >= :periodoInicio)")
    long countSolapamiento(
            @Param("periodoInicio") String periodoInicio,
            @Param("periodoFin") String periodoFin,
            @Param("idExcluir") Long idExcluir);
}
