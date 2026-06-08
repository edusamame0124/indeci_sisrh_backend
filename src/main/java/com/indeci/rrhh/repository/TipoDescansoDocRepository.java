package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.TipoDescansoDoc;

public interface TipoDescansoDocRepository extends JpaRepository<TipoDescansoDoc, Long> {

List<TipoDescansoDoc>
findByTipoDescansoIdAndActivo(
    Long tipoDescansoId,
    Integer activo);




}



