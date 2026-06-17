package com.indeci.rrhh.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.rrhh.entity.ConocimientoInformatico;

public interface ConocimientoInformaticoRepository
extends JpaRepository<ConocimientoInformatico, Long> {

    List<ConocimientoInformatico>
    findByEmpleadoIdAndActivoOrderByHerramientaAsc(
            Long empleadoId,
            Integer activo);
}