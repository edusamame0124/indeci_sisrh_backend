package com.indeci.rrhh.repository;



import com.indeci.rrhh.entity.MedidaDisciplinaria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedidaDisciplinariaRepository
        extends JpaRepository<MedidaDisciplinaria, Long> {

    List<MedidaDisciplinaria>
    findByEmpleadoIdAndActivoOrderByFechaInicioDesc(
            Long empleadoId,
            Integer activo);
}