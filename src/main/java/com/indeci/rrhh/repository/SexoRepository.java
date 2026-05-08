package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Sexo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SexoRepository
        extends JpaRepository<Sexo, Long> {
}