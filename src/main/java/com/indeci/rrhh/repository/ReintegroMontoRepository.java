package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.ReintegroMonto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReintegroMontoRepository extends JpaRepository<ReintegroMonto, Long> {
    List<ReintegroMonto> findByPeriodoDestino(String periodoDestino);

    /** F2 — Detección de candidatos: reintegros pendientes del período destino. */
    List<ReintegroMonto> findByPeriodoDestinoAndEstadoPago(String periodoDestino, String estadoPago);

    /** F2 — Pago: reintegros pendientes de un empleado en el período destino. */
    List<ReintegroMonto> findByEmpleadoIdAndPeriodoDestinoAndEstadoPago(
            Long empleadoId, String periodoDestino, String estadoPago);
}
