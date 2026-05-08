package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.TipoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoDocumentoRepository
        extends JpaRepository<TipoDocumento, Long> {
}