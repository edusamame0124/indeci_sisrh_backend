package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.TipoPersonaMef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TipoPersonaMefRepository extends JpaRepository<TipoPersonaMef, Long> {
    List<TipoPersonaMef> findByActivoOrderByCodigoAsc(Integer activo);
}
