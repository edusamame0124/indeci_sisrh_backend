package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Cargo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CargoRepository
        extends JpaRepository<Cargo, Long> {

    List<Cargo>
    findByActivoOrderByNombreAsc(
            Integer activo);

    List<Cargo>
    findByTipoCargoIdAndActivoOrderByNombreAsc(
            Long tipoCargoId,
            Integer activo);
}