package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EstructuraOrganica;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstructuraOrganicaRepository
        extends JpaRepository<EstructuraOrganica, Long> {
}