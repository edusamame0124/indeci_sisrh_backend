package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.MetaPptoAud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetaPptoAudRepository extends JpaRepository<MetaPptoAud, Long> {

    List<MetaPptoAud> findByEmpMetaIdOrderByFechaDesc(Long empMetaId);

    List<MetaPptoAud> findByEmpleadoIdAndAnioFiscalOrderByFechaDesc(Long empleadoId, Integer anioFiscal);

    List<MetaPptoAud> findByAccionOrderByFechaDesc(String accion);
}
