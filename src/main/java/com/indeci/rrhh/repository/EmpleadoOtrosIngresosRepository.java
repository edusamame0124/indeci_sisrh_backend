package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpleadoOtrosIngresos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmpleadoOtrosIngresosRepository extends JpaRepository<EmpleadoOtrosIngresos, Long> {

    /**
     * Busca los ingresos declarados por el empleado correspondientes a otros empleadores
     * en un determinado año fiscal.
     * @param empleadoId El ID del empleado
     * @param anioFiscal El año fiscal a consultar
     * @param activo 1 para activos, 0 para inactivos
     * @return Opcional del registro encontrado
     */
    Optional<EmpleadoOtrosIngresos> findByEmpleadoIdAndAnioFiscalAndActivo(Long empleadoId, Integer anioFiscal, Integer activo);
}
