package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.indeci.rrhh.entity.SolicitudTeletrabajoDet;

@Repository
public interface SolicitudTeletrabajoDetRepository
        extends JpaRepository<SolicitudTeletrabajoDet, Long> {

    List<SolicitudTeletrabajoDet>
    findBySolicitudIdAndActivoOrderByNroOrden(
            Long solicitudId,
            Integer activo);

    void deleteBySolicitudId(
            Long solicitudId);
}
