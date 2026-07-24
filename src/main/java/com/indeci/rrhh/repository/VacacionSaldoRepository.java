package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.VacacionSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VacacionSaldoRepository extends JpaRepository<VacacionSaldo, Long> {

    List<VacacionSaldo> findByEmpleadoIdAndActivo(Long empleadoId, Integer activo);

    /**
     * Empleados con baseline vacacional IMPORTADO (para "Provisionar para todos").
     * Marcador estable: la fila importada trae {@code fechaCorte} (col K del Excel) y/o
     * {@code origen='MIGRACION_INICIAL_2026'}; la provisión conserva {@code fechaCorte}
     * hacia adelante, de modo que el conjunto sigue identificable tras re-provisionar.
     */
    @Query("select distinct v.empleadoId from VacacionSaldo v "
            + "where v.activo = 1 and (v.fechaCorte is not null or v.origen = 'MIGRACION_INICIAL_2026')")
    List<Long> findEmpleadoIdsImportados();

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
