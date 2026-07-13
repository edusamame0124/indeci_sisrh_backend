package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.SolicitudRrhh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SolicitudRrhhRepository
        extends JpaRepository<SolicitudRrhh, Long> {

    /**
     * Papeletas APROBADAS y activas del empleado, cuyo tipo JUSTIFICA la
     * asistencia (con goce / teletrabajo), que podrian cubrir el periodo
     * (fecha de inicio no posterior a {@code hasta}). El descarte fino por
     * fecha exacta se hace en memoria (cubreFecha), respetando FECHA_FIN nula.
     * Trae el tipo (join fetch) para conocer el codigo sin lazy loading extra.
     */
    @Query("select s from SolicitudRrhh s join fetch s.tipoSolicitud t "
            + "where s.empleadoId = :empleadoId "
            + "and s.activo = 1 "
            + "and s.estadoSolicitudId = :estadoAprobada "
            + "and t.justificaAsistencia = 1 "
            + "and s.fechaInicio is not null "
            + "and s.fechaInicio <= :hasta")
    List<SolicitudRrhh> findJustificantesAsistencia(
            @Param("empleadoId") Long empleadoId,
            @Param("estadoAprobada") Long estadoAprobada,
            @Param("hasta") LocalDate hasta);

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