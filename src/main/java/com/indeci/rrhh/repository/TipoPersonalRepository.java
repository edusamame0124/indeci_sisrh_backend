package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.TipoPersonal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoPersonalRepository
        extends JpaRepository<TipoPersonal, Long> {
}