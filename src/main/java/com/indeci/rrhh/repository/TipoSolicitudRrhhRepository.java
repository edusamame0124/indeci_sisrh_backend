package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.TipoSolicitudRrhh;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoSolicitudRrhhRepository
        extends JpaRepository<TipoSolicitudRrhh, Long> {
	
	 Optional<TipoSolicitudRrhh> findByCodigo(String codigo);
}