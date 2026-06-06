package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpleadoBanco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmpleadoBancoRepository extends JpaRepository<EmpleadoBanco, Long> {

    List<EmpleadoBanco> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);
    
    List<EmpleadoBanco> findByEmpleadoId(Long empleadoId);
    
    Optional<EmpleadoBanco> findByEmpleadoIdAndEsCuentaPlanillaAndActivo(
    	    Long empleadoId,
    	    Integer esCuentaPlanilla,
    	    Integer activo
    	);

    /** P0 Planilla CAS Consolidada — cuentas principales activas en batch (sin N+1). */
    List<EmpleadoBanco> findByEmpleadoIdInAndEsCuentaPlanillaAndActivo(
            List<Long> empleadoIds,
            Integer esCuentaPlanilla,
            Integer activo);

    /** Spec 012 / C3 (BKD-006) — paso «banco» del flujo de empleado. */
    boolean existsByEmpleadoIdAndActivo(Long empleadoId, Integer activo);
}