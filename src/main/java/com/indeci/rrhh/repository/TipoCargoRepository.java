package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.TipoCargo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoCargoRepository
        extends JpaRepository<TipoCargo, Long> {

    List<TipoCargo>
    findByActivoOrderByNombreAsc(
            Integer activo);

    Optional<TipoCargo>
    findByCodigo(
            String codigo);
}