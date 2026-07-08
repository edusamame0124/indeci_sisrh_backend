package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {

    List<Feriado> findByAnioInAndActivo(Collection<Integer> anios, Integer activo);

    List<Feriado> findByAnioAndActivoOrderByFecha(Integer anio, Integer activo);
}
