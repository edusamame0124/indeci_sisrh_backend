package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.SolicitudRrhhDoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudRrhhDocRepository
        extends JpaRepository<
        SolicitudRrhhDoc,
        Long> {

    List<SolicitudRrhhDoc>
    findBySolicitudIdAndActivoOrderByVersionDocAsc(
            Long solicitudId,
            Integer activo);

    Optional<SolicitudRrhhDoc>
    findTopBySolicitudIdOrderByVersionDocDesc(
            Long solicitudId);
}