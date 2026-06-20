package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MetaPptoEquiv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MetaPptoEquivRepository extends JpaRepository<MetaPptoEquiv, Long> {

    List<MetaPptoEquiv> findByAnioOrigenAndAnioDestinoOrderByMetaOrigenId(Integer anioOrigen, Integer anioDestino);

    /** Equivalencia activa hacia el año destino para una meta origen. */
    @Query("SELECT e FROM MetaPptoEquiv e WHERE e.metaOrigenId = :metaOrigenId " +
           "AND e.anioDestino = :anioDestino AND e.activo = 1 AND e.estado <> 'ANULADO'")
    Optional<MetaPptoEquiv> findActivaByOrigenYDestino(
            @Param("metaOrigenId") Long metaOrigenId,
            @Param("anioDestino") Integer anioDestino);

    /** Todas las equivalencias activas para un par de años. */
    @Query("SELECT e FROM MetaPptoEquiv e WHERE e.anioOrigen = :origen " +
           "AND e.anioDestino = :destino AND e.activo = 1 AND e.estado <> 'ANULADO'")
    List<MetaPptoEquiv> findActivasByAnios(@Param("origen") Integer anioOrigen, @Param("destino") Integer anioDestino);

    boolean existsByMetaOrigenIdAndAnioDestinoAndActivoAndEstadoNot(
            Long metaOrigenId, Integer anioDestino, Integer activo, String estado);

    @Query("SELECT COUNT(e) FROM MetaPptoEquiv e WHERE e.anioDestino = :anioDestino AND e.activo = 1 AND e.estado <> 'ANULADO'")
    long countActivasByAnioDestino(@Param("anioDestino") Integer anioDestino);
}
