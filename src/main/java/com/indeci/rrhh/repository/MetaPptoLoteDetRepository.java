package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MetaPptoLoteDet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MetaPptoLoteDetRepository extends JpaRepository<MetaPptoLoteDet, Long> {

    List<MetaPptoLoteDet> findByLoteIdOrderByEmpleadoId(Long loteId);

    List<MetaPptoLoteDet> findByLoteIdAndEstadoValidacion(Long loteId, String estadoValidacion);

    Optional<MetaPptoLoteDet> findByLoteIdAndEmpleadoId(Long loteId, Long empleadoId);

    @Query("SELECT d FROM MetaPptoLoteDet d WHERE d.loteId = :loteId AND d.estadoValidacion <> 'OK'")
    List<MetaPptoLoteDet> findObservadosByLote(@Param("loteId") Long loteId);

    @Query("SELECT COUNT(d) FROM MetaPptoLoteDet d WHERE d.loteId = :loteId AND d.estadoValidacion = 'OK'")
    long countOkByLote(@Param("loteId") Long loteId);

    @Query("SELECT COUNT(d) FROM MetaPptoLoteDet d WHERE d.loteId = :loteId AND d.estadoValidacion <> 'OK'")
    long countObservadosByLote(@Param("loteId") Long loteId);

    void deleteByLoteId(Long loteId);
}
