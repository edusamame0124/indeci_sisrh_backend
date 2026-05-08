package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Profesion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfesionRepository
        extends JpaRepository<Profesion, Long> {
}