package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.TtModalidad;

@Repository
public interface TtModalidadRepository
        extends JpaRepository<TtModalidad, Long> {

    List<TtModalidad>
    findByActivoOrderByNombreAsc(
            Integer activo);
}