package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.TtEstadoCumplimiento;

@Repository
public interface TtEstadoCumplimientoRepository
        extends JpaRepository<
                TtEstadoCumplimiento,
                Long> {

    List<TtEstadoCumplimiento>
    findByActivoOrderByNombreAsc(
            Integer activo);
}