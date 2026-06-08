package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.TipoLicencia;

public interface TipoLicenciaRepository
extends JpaRepository<TipoLicencia, Long> {

List<TipoLicencia>
findByActivoOrderByNombreAsc(
    Integer activo);
}