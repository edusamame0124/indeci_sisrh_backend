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
}