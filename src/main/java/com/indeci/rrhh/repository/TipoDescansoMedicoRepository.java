package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.TipoDescansoMedico;

@Repository
public interface TipoDescansoMedicoRepository
        extends JpaRepository<TipoDescansoMedico, Long> {

    List<TipoDescansoMedico>
    findByActivoOrderByNombreAsc(
            Integer activo);
}