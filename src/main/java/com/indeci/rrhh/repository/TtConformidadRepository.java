package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.TtConformidad;

@Repository
public interface TtConformidadRepository
        extends JpaRepository<
                TtConformidad,
                Long> {

    List<TtConformidad>
    findByActivoOrderByNombreAsc(
            Integer activo);
}