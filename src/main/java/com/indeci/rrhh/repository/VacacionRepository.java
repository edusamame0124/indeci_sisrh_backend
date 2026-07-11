package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.Vacacion;

@Repository
public interface VacacionRepository extends JpaRepository<Vacacion, Long>{
	
	List<Vacacion>findByEmpleadoIdAndActivo(Long empleadoId,Integer activo);
	
	boolean existsByEmpleadoIdAndPeriodo(Long idEmpleado, String Periodo);
	
	Optional<Vacacion>
	findFirstByEmpleadoIdAndActivoAndPeriodoDesdeLessThanEqualAndPeriodoHastaGreaterThanEqual(
	        Long empleadoId,
	        Integer activo,
	        LocalDate fecha1,
	        LocalDate fecha2);

	/**
	 * Hub Vacacional — periodos aprobados aún no gozados (futuros) y no sustituidos por una
	 * reprogramación/fraccionamiento previa. Fuente del dropdown Poka-Yoke.
	 */
	List<Vacacion> findByEmpleadoIdAndActivoAndPeriodoDesdeGreaterThanEqualAndEstadoNot(
	        Long empleadoId,
	        Integer activo,
	        LocalDate periodoDesde,
	        String estado);

}
