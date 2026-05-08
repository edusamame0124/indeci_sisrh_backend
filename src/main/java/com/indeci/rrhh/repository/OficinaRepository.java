package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Oficina;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OficinaRepository
        extends JpaRepository<Oficina, Long> {

    List<Oficina> findBySedeId(Long sedeId);
}