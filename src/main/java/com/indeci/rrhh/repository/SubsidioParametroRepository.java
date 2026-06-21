package com.indeci.rrhh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.SubsidioParametro;

public interface SubsidioParametroRepository extends JpaRepository<SubsidioParametro, Long> {

    Optional<SubsidioParametro> findByCodigoAndActivo(String codigo, Integer activo);
}
