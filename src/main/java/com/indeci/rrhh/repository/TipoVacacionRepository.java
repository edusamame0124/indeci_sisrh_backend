package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.TipoVacacion;

@Repository
public interface TipoVacacionRepository
        extends JpaRepository<TipoVacacion, Long> {

    List<TipoVacacion>
    findByActivoOrderByNombreAsc(
            Integer activo);
}