package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsistenciaImportacionFilaRepository
        extends JpaRepository<AsistenciaImportacionFila, Long> {

    List<AsistenciaImportacionFila> findByImportacionIdOrderByNumeroFila(Long importacionId);

    void deleteByImportacionId(Long importacionId);
}
