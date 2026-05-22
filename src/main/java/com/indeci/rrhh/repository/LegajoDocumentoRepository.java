package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.LegajoDocumento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegajoDocumentoRepository
        extends JpaRepository<LegajoDocumento, Long> {

    List<LegajoDocumento>
    findByEmpleadoIdAndActivoOrderByCreatedAtDesc(
            Long empleadoId,
            Integer activo);

    List<LegajoDocumento>
    findByEmpleadoIdAndCategoriaIdAndActivoOrderByCreatedAtDesc(
            Long empleadoId,
            Long categoriaId,
            Integer activo);

    List<LegajoDocumento>
    findByEmpleadoIdAndSubcategoriaIdAndActivoOrderByCreatedAtDesc(
            Long empleadoId,
            Long subcategoriaId,
            Integer activo);
}