package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EstadoSolicitud;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoSolicitudRepository
        extends JpaRepository<EstadoSolicitud, Long> {
	
	
	Optional<EstadoSolicitud>
	findByCodigo(String codigo);
	
}