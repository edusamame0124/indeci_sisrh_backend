package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.TipoDescansoDoc;

public interface TipoDescansoDocRepository extends JpaRepository<TipoDescansoDoc, Long> {


@Query("""
        SELECT t
        FROM TipoDescansoDoc t
        JOIN FETCH t.documento
        WHERE t.tipoDescansoId = :tipoDescansoId
        AND t.activo = :activo
    """)
    List<TipoDescansoDoc> findByTipoDescansoIdAndActivo(
            @Param("tipoDescansoId") Long tipoDescansoId,
            @Param("activo") Integer activo);

}



