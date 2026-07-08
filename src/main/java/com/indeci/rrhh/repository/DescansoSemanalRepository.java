package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.DescansoSemanal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DescansoSemanalRepository extends JpaRepository<DescansoSemanal, Long> {

    List<DescansoSemanal> findByActivo(Integer activo);
}
