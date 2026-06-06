package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.TipoPersona;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoPersonalRepository
        extends JpaRepository<TipoPersona, Long> {
}