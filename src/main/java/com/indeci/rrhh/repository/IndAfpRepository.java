package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.IndAfp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IndAfpRepository extends JpaRepository<IndAfp, Long> {

    List<IndAfp> findByActivoOrderByNombreAsc(Integer activo);

    Optional<IndAfp> findByCodigoIgnoreCase(String codigo);

    Optional<IndAfp> findFirstByNombreContainingIgnoreCase(String nombre);
}
