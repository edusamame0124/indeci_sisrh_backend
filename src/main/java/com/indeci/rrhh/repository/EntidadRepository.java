package com.indeci.rrhh.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.Entidad;

public interface EntidadRepository
        extends JpaRepository<Entidad, String> {
}
