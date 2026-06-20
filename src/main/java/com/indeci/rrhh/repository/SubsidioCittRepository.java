package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.SubsidioCitt;

public interface SubsidioCittRepository extends JpaRepository<SubsidioCitt, Long> {

    List<SubsidioCitt> findByCasoIdAndActivoOrderByFechaInicioAsc(Long casoId, Integer activo);

    Optional<SubsidioCitt> findByIdAndActivo(Long id, Integer activo);
}
