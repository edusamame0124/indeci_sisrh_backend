package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AsistenciaImportacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsistenciaImportacionRepository
        extends JpaRepository<AsistenciaImportacion, Long> {

    Page<AsistenciaImportacion> findByPeriodoOrderByFechaImportacionDesc(
            String periodo,
            Pageable pageable);

    Page<AsistenciaImportacion> findAllByOrderByFechaImportacionDesc(Pageable pageable);

    /** F2/F4 — detección de archivo duplicado por hash SHA-256 (req 20). */
    boolean existsByHashSha256AndIdNot(String hashSha256, Long id);

    /**
     * P0 fix — borradores sin confirmar de un periodo, para descartarlos cuando
     * se genera un nuevo preview (evita acumular un juego completo de filas por
     * cada reintento, p. ej. el mapeo iterativo de alias COEN).
     */
    List<AsistenciaImportacion> findByPeriodoAndEstado(String periodo, String estado);
}
