package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.CondicionLaboral;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CondicionLaboralRepository
        extends JpaRepository<CondicionLaboral, Long> {
}