package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Eps;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EpsRepository extends JpaRepository<Eps, Long> {
    List<Eps> findByActivoOrderByNombreAsc(Integer activo);
}
