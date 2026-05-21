package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    List<Prestamo> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);
}
