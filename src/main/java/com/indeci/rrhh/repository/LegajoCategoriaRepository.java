package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.LegajoCategoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegajoCategoriaRepository
        extends JpaRepository<LegajoCategoria, Long> {

    List<LegajoCategoria>
    findByActivoOrderByOrdenVisualAsc(
            Integer activo);
}