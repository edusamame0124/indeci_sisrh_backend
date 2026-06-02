package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.TipoEvento;

/**
 * F2.1 — Catálogo de tipos de evento. Read-only en su mayoría; los seeds
 * vienen de V010_43.
 */
public interface TipoEventoRepository extends JpaRepository<TipoEvento, Long> {

    Optional<TipoEvento> findByCodigo(String codigo);

    List<TipoEvento> findByActivoOrderByOrdenVisualAsc(Integer activo);
}
