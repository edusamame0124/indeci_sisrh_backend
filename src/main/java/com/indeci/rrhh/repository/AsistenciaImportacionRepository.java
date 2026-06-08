package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AsistenciaImportacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsistenciaImportacionRepository
        extends JpaRepository<AsistenciaImportacion, Long> {

    Page<AsistenciaImportacion> findByPeriodoOrderByFechaImportacionDesc(
            String periodo,
            Pageable pageable);

    Page<AsistenciaImportacion> findAllByOrderByFechaImportacionDesc(Pageable pageable);
}
