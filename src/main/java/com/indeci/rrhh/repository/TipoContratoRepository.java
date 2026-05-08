package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.TipoContrato;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoContratoRepository
        extends JpaRepository<TipoContrato, Long> {
}