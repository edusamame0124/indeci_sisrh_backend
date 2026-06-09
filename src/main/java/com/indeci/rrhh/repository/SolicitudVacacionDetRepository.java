package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.SolicitudVacacionDet;

@Repository
public interface SolicitudVacacionDetRepository
        extends JpaRepository<SolicitudVacacionDet, Long> {

    List<SolicitudVacacionDet>
    findBySolicitudIdAndActivo(
            Long solicitudId,
            Integer activo);

    void deleteBySolicitudId(
            Long solicitudId);
}