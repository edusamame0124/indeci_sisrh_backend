package com.indeci.rrhh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.SubsidioBaseHistorica;

public interface SubsidioBaseHistoricaRepository extends JpaRepository<SubsidioBaseHistorica, Long> {

    Optional<SubsidioBaseHistorica> findByCasoIdAndEsVigente(Long casoId, String esVigente);

    @Modifying
    @Query("UPDATE SubsidioBaseHistorica b SET b.esVigente = 'N' WHERE b.casoId = :casoId")
    void desactivarVigentesPorCaso(@Param("casoId") Long casoId);

    List<SubsidioBaseHistorica> findByCasoIdOrderByVersionBaseDesc(Long casoId);
}
