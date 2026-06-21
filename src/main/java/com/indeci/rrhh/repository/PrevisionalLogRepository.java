package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.PrevisionalLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrevisionalLogRepository extends JpaRepository<PrevisionalLog, Long> {

    List<PrevisionalLog> findAllByOrderByFechaDesc();

    Optional<PrevisionalLog> findFirstByTipoOrderByFechaDesc(String tipo);
}
