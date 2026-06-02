package com.indeci.sistema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.sistema.entity.SistemaRol;

public interface SistemaRolRepository extends JpaRepository<SistemaRol, Long> {

    List<SistemaRol> findBySistemaIdAndActivoOrderByOrdenAscCodigoRolAsc(Long sistemaId, Integer activo);

    boolean existsBySistemaIdAndCodigoRolAndActivo(Long sistemaId, String codigoRol, Integer activo);
}
