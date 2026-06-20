package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.JornadaRegimen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JornadaRegimenRepository extends JpaRepository<JornadaRegimen, Long> {

    Optional<JornadaRegimen> findByRegimenLaboralId(Long regimenLaboralId);
}
