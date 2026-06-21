package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MetaPptoLote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetaPptoLoteRepository extends JpaRepository<MetaPptoLote, Long> {

    Optional<MetaPptoLote> findByCodigoLote(String codigoLote);

    List<MetaPptoLote> findByAnioDestinoOrderByCreadoEnDesc(Integer anioDestino);

    List<MetaPptoLote> findByAnioDestinoAndEstadoOrderByCreadoEnDesc(Integer anioDestino, String estado);

    List<MetaPptoLote> findByEstadoOrderByCreadoEnDesc(String estado);

    boolean existsByAnioDestinoAndEstado(Integer anioDestino, String estado);
}
