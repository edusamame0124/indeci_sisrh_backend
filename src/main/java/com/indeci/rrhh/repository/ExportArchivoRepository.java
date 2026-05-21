package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.ExportArchivo;

public interface ExportArchivoRepository
        extends JpaRepository<ExportArchivo, Long> {

    /** Historial de exportaciones de un período (más reciente primero). */
    List<ExportArchivo> findByPeriodoOrderByFechaGeneradoDesc(String periodo);

    /** Última exportación de un tipo en un período (para idempotencia / re-emisión). */
    Optional<ExportArchivo>
    findFirstByPeriodoAndTipoArchivoOrderByFechaGeneradoDesc(
            String periodo, String tipoArchivo);
}
