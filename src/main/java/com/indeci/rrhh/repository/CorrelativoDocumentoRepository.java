package com.indeci.rrhh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.indeci.rrhh.entity.CorrelativoDocumento;

import jakarta.persistence.LockModeType;

public interface CorrelativoDocumentoRepository
        extends JpaRepository<CorrelativoDocumento, Long> {

    /**
     * Lee la fila del correlativo con lock pesimista de escritura para que la
     * emisión de NRO_PLANILLA sea atómica bajo concurrencia. Debe llamarse dentro
     * de una transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CorrelativoDocumento>
    findByCodEntidadAndAnioAndMesAndTipoDocumento(
            String codEntidad, Integer anio, Integer mes, String tipoDocumento);
}
