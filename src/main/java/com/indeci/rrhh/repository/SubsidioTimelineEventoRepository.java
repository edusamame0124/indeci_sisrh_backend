package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.SubsidioTimelineEvento;

public interface SubsidioTimelineEventoRepository extends JpaRepository<SubsidioTimelineEvento, Long> {

    List<SubsidioTimelineEvento> findByCasoIdOrderByCreatedAtDesc(Long casoId);
}
