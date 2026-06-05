package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.ConceptoPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConceptoPlanillaRepository
        extends JpaRepository<ConceptoPlanilla, Long> {

    List<ConceptoPlanilla> findByActivo(Integer activo);
    
    Optional<ConceptoPlanilla>
    findByCodigoAndActivo(
            String codigo,
            Integer activo);

    /** Spec 010 — búsqueda por código MEF oficial (LEY-01). */
    @Query(value = """
            SELECT *
              FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
             WHERE CODIGO_MEF = :codigoMef
               AND ACTIVO = :activo
             ORDER BY FECHA_VIG_INI DESC NULLS LAST,
                      CREATED_AT DESC NULLS LAST,
                      ID DESC
             FETCH FIRST 1 ROWS ONLY
            """, nativeQuery = true)
    Optional<ConceptoPlanilla>
    findByCodigoMefAndActivo(
            @Param("codigoMef") String codigoMef,
            @Param("activo") Integer activo);

    /** B3 — conceptos activos con un código PLAME SUNAT dado (muchos-a-uno). */
    List<ConceptoPlanilla>
    findByCodigoPlameSunatAndActivo(
            String codigoPlameSunat,
            Integer activo);

    /** B3 — conceptos activos con un código MCPP dado (muchos-a-uno). */
    List<ConceptoPlanilla>
    findByCodigoMcppAndActivo(
            String codigoMcpp,
            Integer activo);
}
