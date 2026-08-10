package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.RegimenLaboral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegimenLaboralRepository
        extends JpaRepository<RegimenLaboral, Long> {

    Optional<RegimenLaboral> findByCodigo(String codigo);
}