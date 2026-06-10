package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.SolicitudCompensacionDet;

@Repository
public interface SolicitudCompensacionDetRepository
        extends JpaRepository<
                SolicitudCompensacionDet,
                Long> {

    List<SolicitudCompensacionDet>
    findBySolicitudIdAndActivo(
            Long solicitudId,
            Integer activo);

    void deleteBySolicitudId(
            Long solicitudId);
}