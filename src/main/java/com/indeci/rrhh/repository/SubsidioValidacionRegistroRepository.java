package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.SubsidioValidacionRegistro;

public interface SubsidioValidacionRegistroRepository
        extends JpaRepository<SubsidioValidacionRegistro, Long> {

    List<SubsidioValidacionRegistro> findByCasoIdAndResueltaOrderByCreatedAtDesc(
            Long casoId, String resuelta);

    List<SubsidioValidacionRegistro> findByTramoIdOrderByCreatedAtDesc(Long tramoId);

    List<SubsidioValidacionRegistro> findByLiquidacionIdOrderByCreatedAtDesc(Long liquidacionId);
}
