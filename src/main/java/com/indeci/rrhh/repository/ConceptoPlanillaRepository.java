package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.ConceptoPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;

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
    Optional<ConceptoPlanilla>
    findByCodigoMefAndActivo(
            String codigoMef,
            Integer activo);

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