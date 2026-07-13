package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.VacacionSaldo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VacacionSaldoRepository extends JpaRepository<VacacionSaldo, Long> {

    List<VacacionSaldo> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    /** SPEC_VACACIONES F4 — batch de saldos para el padrón (evita N+1). */
    List<VacacionSaldo> findByEmpleadoIdInAndActivo(List<Long> empleadoIds, Integer activo);

    Optional<VacacionSaldo> findByEmpleadoIdAndAnioAndActivo(
            Long empleadoId, Integer anio, Integer activo);

    List<VacacionSaldo> findByEmpleadoIdAndActivoOrderByAnioAsc(
            Long empleadoId, Integer activo);

    /**
     * Historial COMPLETO (activos + anulados) — Trazabilidad Visual de "Provisionar Auto".
     * A diferencia de todos los demás métodos de este repositorio, NO filtra por activo:
     * es exclusivamente para el modal de auditoría, nunca para cálculos de saldo.
     */
    List<VacacionSaldo> findByEmpleadoIdOrderByAnioDescCreatedAtDesc(Long empleadoId);
}
