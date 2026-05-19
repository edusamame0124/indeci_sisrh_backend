package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.SolicitudRrhhHist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudRrhhHistRepository
        extends JpaRepository<
        SolicitudRrhhHist,
        Long> {

    List<SolicitudRrhhHist>
    findBySolicitudIdOrderByFechaAsc(
            Long solicitudId);
}