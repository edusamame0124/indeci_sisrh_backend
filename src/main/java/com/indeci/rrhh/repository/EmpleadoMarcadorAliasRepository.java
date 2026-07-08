package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.EmpleadoMarcadorAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmpleadoMarcadorAliasRepository
        extends JpaRepository<EmpleadoMarcadorAlias, Long> {

    /** Resolución del alias en la importación COEN (por nombre normalizado). */
    Optional<EmpleadoMarcadorAlias> findFirstByNombreMarcadorNormAndActivo(
            String nombreMarcadorNorm, Integer activo);

    /** Alias existentes de un empleado (para la pantalla de mapeo). */
    List<EmpleadoMarcadorAlias> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    boolean existsByNombreMarcadorNormAndActivo(String nombreMarcadorNorm, Integer activo);
}
