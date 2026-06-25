package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.NivelInstruccion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NivelInstruccionRepository
        extends JpaRepository<NivelInstruccion, Long> {
	
	
	 List<NivelInstruccion>
	    findByActivoOrderByNombreAsc(
	            Integer activo);
}