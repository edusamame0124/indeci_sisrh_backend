package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.LegajoSubcategoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegajoSubcategoriaRepository
        extends JpaRepository<LegajoSubcategoria, Long> {

    List<LegajoSubcategoria>
    findByCategoriaIdAndActivoOrderByOrdenVisualAsc(
            Long categoriaId,
            Integer activo);
}