package com.indeci.rrhh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.SubsidioReglaFormula;

public interface SubsidioReglaFormulaRepository extends JpaRepository<SubsidioReglaFormula, Long> {

    Optional<SubsidioReglaFormula> findByReglaVigenciaIdAndCodigoFormulaAndActivo(
            Long reglaVigenciaId, String codigoFormula, Integer activo);
}
