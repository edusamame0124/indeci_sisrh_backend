package com.indeci.sistema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.sistema.entity.SistemaArea;

public interface SistemaAreaRepository extends JpaRepository<SistemaArea, Long> {

    List<SistemaArea> findBySistemaIdAndActivoOrderByOrdenAscCodigoAreaAsc(Long sistemaId, Integer activo);

    boolean existsBySistemaIdAndCodigoAreaAndActivo(Long sistemaId, String codigoArea, Integer activo);
}
