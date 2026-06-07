package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.SolicitudRrhh;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SolicitudRrhhRepository
        extends JpaRepository<SolicitudRrhh, Long> {

    List<SolicitudRrhh>
    findByEmpleadoIdAndActivo(
            Long empleadoId,
            Integer activo);

    List<SolicitudRrhh>
    findByActivo(Integer activo);
    
	boolean existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
	        Long empleadoId,
	        Long tipoSolicitudId,
	        Integer activo,
	        LocalDate fechaFin,
	        LocalDate fechaInicio
	);
	
	List<SolicitudRrhh>
	findByEmpleadoIdInAndActivo(
	        List<Long> empleadoIds,
	        Integer activo);
	
	
	List<SolicitudRrhh>
	findByEmpleadoIdAndTipoSolicitudIdAndEstadoSolicitudIdAndActivo(
	        Long empleadoId,
	        Long tipoSolicitudId,
	        Long estadoSolicitudId,
	        Integer activo);
	
	
	boolean existsByIdNotAndEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
	        Long id,
	        Long empleadoId,
	        Long tipoSolicitudId,
	        Integer activo,
	        LocalDate fechaFin,
	        LocalDate fechaInicio);
}