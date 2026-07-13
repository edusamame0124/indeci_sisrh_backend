package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.VacacionAcumulacionDecision;

@Repository
public interface VacacionAcumulacionDecisionRepository
        extends JpaRepository<VacacionAcumulacionDecision, Long> {

    List<VacacionAcumulacionDecision> findByEmpleadoIdAndActivoOrderByCreatedAtDesc(
            Long empleadoId, Integer activo);
}
