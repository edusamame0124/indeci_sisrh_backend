package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.SubsidioReglaConcepto;

public interface SubsidioReglaConceptoRepository extends JpaRepository<SubsidioReglaConcepto, Long> {

    List<SubsidioReglaConcepto> findByReglaVigenciaIdAndActivo(Long reglaVigenciaId, Integer activo);

    Optional<SubsidioReglaConcepto> findByReglaVigenciaIdAndTipoSubsidioAndTipoImputacionAndActivo(
            Long reglaVigenciaId, String tipoSubsidio, String tipoImputacion, Integer activo);
}
