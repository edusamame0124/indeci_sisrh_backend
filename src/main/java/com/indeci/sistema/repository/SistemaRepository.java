package com.indeci.sistema.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.sistema.entity.Sistema;

public interface SistemaRepository extends JpaRepository<Sistema, Long> {

    Optional<Sistema> findByCodigo(String codigo);

    /** Sistemas visibles en el Portal Selector, ordenados por ORDEN ASC. */
    List<Sistema> findByActivoOrderByOrdenAsc(Integer activo);
}
