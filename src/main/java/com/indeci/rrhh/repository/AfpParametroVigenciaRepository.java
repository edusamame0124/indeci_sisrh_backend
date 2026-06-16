package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AfpParametroVigencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AfpParametroVigenciaRepository extends JpaRepository<AfpParametroVigencia, Long> {

    @Query("SELECT v FROM AfpParametroVigencia v JOIN FETCH v.afp ORDER BY v.periodoInicio DESC")
    List<AfpParametroVigencia> findAllWithAfp();

    @Query("SELECT v FROM AfpParametroVigencia v JOIN FETCH v.afp " +
           "WHERE (:estado IS NULL OR v.estado = :estado) ORDER BY v.periodoInicio DESC")
    List<AfpParametroVigencia> findByEstado(@Param("estado") String estado);

    @Query("SELECT v FROM AfpParametroVigencia v JOIN FETCH v.afp " +
           "WHERE v.afp.id = :afpId AND v.estado = 'VIGENTE' " +
           "AND v.periodoInicio <= :periodo " +
           "AND (v.periodoFin IS NULL OR v.periodoFin >= :periodo) " +
           "ORDER BY v.periodoInicio DESC")
    Optional<AfpParametroVigencia> findVigenteByAfpAndPeriodo(
            @Param("afpId") Long afpId,
            @Param("periodo") String periodo);

    /** Detecta solapamiento: misma AFP, estados activos, rangos que se cruzan. */
    @Query("SELECT COUNT(v) FROM AfpParametroVigencia v " +
           "WHERE v.afp.id = :afpId " +
           "AND v.estado IN ('VIGENTE','PROGRAMADO') " +
           "AND (:idExcluir IS NULL OR v.id <> :idExcluir) " +
           "AND v.periodoInicio <= COALESCE(:periodoFin, '999999') " +
           "AND (v.periodoFin IS NULL OR v.periodoFin >= :periodoInicio)")
    long countSolapamiento(
            @Param("afpId") Long afpId,
            @Param("periodoInicio") String periodoInicio,
            @Param("periodoFin") String periodoFin,
            @Param("idExcluir") Long idExcluir);
}
