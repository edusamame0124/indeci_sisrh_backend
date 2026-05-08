package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.GradoAcademico;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GradoAcademicoRepository
        extends JpaRepository<GradoAcademico, Long> {
}