package com.indeci.sistema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.sistema.entity.SistemaArea;

public interface SistemaAreaRepository extends JpaRepository<SistemaArea, Long> {

    List<SistemaArea> findBySistemaIdAndActivoOrderByOrdenAscCodigoAreaAsc(Long sistemaId, Integer activo);

    boolean existsBySistemaIdAndCodigoAreaAndActivo(Long sistemaId, String codigoArea, Integer activo);

    /** TRUE si el sistema tiene catalogo de areas activas (entonces la oficina es obligatoria al activar acceso). */
    boolean existsBySistemaIdAndActivo(Long sistemaId, Integer activo);
}
