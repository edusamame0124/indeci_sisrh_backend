package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MetaPptoCat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MetaPptoCatRepository extends JpaRepository<MetaPptoCat, Long> {

    List<MetaPptoCat> findByAnioFiscalOrderByMetaCodigo(Integer anioFiscal);

    List<MetaPptoCat> findByAnioFiscalAndEstadoOrderByMetaCodigo(Integer anioFiscal, String estado);

    List<MetaPptoCat> findByAnioFiscalAndActivoOrderByMetaCodigo(Integer anioFiscal, Integer activo);

    Optional<MetaPptoCat> findByMetaHash(String metaHash);

    boolean existsByMetaHash(String metaHash);

    @Query("SELECT m FROM MetaPptoCat m WHERE m.anioFiscal = :anio AND m.estado <> 'ANULADO' ORDER BY m.metaCodigo")
    List<MetaPptoCat> findActivasByAnio(@Param("anio") Integer anio);

    @Query("SELECT COUNT(m) FROM MetaPptoCat m WHERE m.anioFiscal = :anio AND m.estado = 'PUBLICADO'")
    long countPublicadasByAnio(@Param("anio") Integer anio);

    @Query("SELECT m FROM MetaPptoCat m WHERE m.anioFiscal = :anio AND m.metaCodigo = :codigo AND m.estado <> 'ANULADO'")
    List<MetaPptoCat> findByAnioAndCodigo(@Param("anio") Integer anio, @Param("codigo") String codigo);

    /** Busca metas por estructura (sin META_CODIGO) para detección automática de equivalencias. */
    @Query("SELECT m FROM MetaPptoCat m WHERE m.anioFiscal = :anio " +
           "AND m.centroCosto = :centroCosto " +
           "AND m.categoriaPresupuestal = :categoria " +
           "AND m.producto = :producto " +
           "AND m.actividad = :actividad " +
           "AND m.finalidad = :finalidad " +
           "AND m.estado <> 'ANULADO'")
    List<MetaPptoCat> findByEstructura(
            @Param("anio") Integer anioFiscal,
            @Param("centroCosto") String centroCosto,
            @Param("categoria") String categoriaPresupuestal,
            @Param("producto") String producto,
            @Param("actividad") String actividad,
            @Param("finalidad") String finalidad);
}
