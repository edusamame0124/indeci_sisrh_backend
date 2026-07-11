package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.AsistenciaDetalle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AsistenciaDetalleRepository
        extends JpaRepository<AsistenciaDetalle, Long> {

    List<AsistenciaDetalle> findByCabeceraIdOrderByDia(Long cabeceraId);

    void deleteByCabeceraId(Long cabeceraId);

    /**
     * SPEC_VACACIONES F9.1 — cuenta faltas (TIPO_DIA IN ('FALTA','SANCION_PAD')) del
     * empleado en [desde, hasta]. Base de las inasistencias no computables al récord
     * vacacional. SANCION_PAD (sanción por PAD) se equipara a FALTA para este cómputo.
     */
    @Query("""
            SELECT COUNT(det)
              FROM AsistenciaDetalle det, AsistenciaCabecera cab
             WHERE det.cabeceraId = cab.id
               AND cab.empleadoId = :empleadoId
               AND det.tipoDia    IN ('FALTA', 'SANCION_PAD')
               AND det.dia BETWEEN :desde AND :hasta
            """)
    long contarFaltas(
            @Param("empleadoId") Long empleadoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Consulta diaria paginada: detalle del día + cabecera activa + persona.
     * Retorna Object[] con columnas en orden fijo (ver AsistenciaService#mapearDiariaRow).
     */
    @Query("""
            SELECT det.id, cab.id, cab.empleadoId, p.dni, p.nombreCompleto, det.dia,
                   det.marcaEntrada, det.marcaSalida, det.tipoDia,
                   det.horasTrabajadasMin, det.minutosSalidaAnticipada, cab.periodo, det.origen,
                   det.minutosTardanza, det.observacion, det.marca3, det.marca4,
                   det.horaEntradaEsperada, det.horasExtra25Min, det.horasExtra35Min,
                   det.horasExtra100Min, det.horasExtraTotalMin,
                   det.papeletaAutorizada, det.papeletaMotivoRechazo
              FROM AsistenciaDetalle det,
                   AsistenciaCabecera cab,
                   Empleado e,
                   Persona p
             WHERE cab.id = det.cabeceraId
               AND e.id = cab.empleadoId
               AND p.id = e.personaId
               AND cab.activo = 1
               AND det.dia = :fecha
               AND (:dni IS NULL OR p.dni LIKE CONCAT('%', :dni, '%'))
               AND (:q IS NULL OR UPPER(p.nombreCompleto) LIKE UPPER(CONCAT('%', :q, '%')))
             ORDER BY p.nombreCompleto, p.dni
            """)
    Page<Object[]> buscarDiaria(
            @Param("fecha") LocalDate fecha,
            @Param("dni") String dni,
            @Param("q") String q,
            Pageable pageable);
    
    @Query("""
            SELECT det.id, cab.id, cab.empleadoId, p.dni, p.nombreCompleto, det.dia,
                   det.marcaEntrada, det.marcaSalida, det.tipoDia,
                   det.horasTrabajadasMin, det.minutosSalidaAnticipada, cab.periodo, det.origen,
                   det.minutosTardanza, det.observacion, det.marca3, det.marca4,
                   det.horaEntradaEsperada, det.horasExtra25Min, det.horasExtra35Min,
                   det.horasExtra100Min, det.horasExtraTotalMin,
                   det.papeletaAutorizada, det.papeletaMotivoRechazo
              FROM AsistenciaDetalle det,
                   AsistenciaCabecera cab,
                   Empleado e,
                   Persona p
             WHERE cab.id = det.cabeceraId
               AND e.id = cab.empleadoId
               AND p.id = e.personaId
               AND cab.activo = 1
               AND cab.empleadoId = :empleadoId
               AND det.dia BETWEEN :fechaInicio AND :fechaFin
             ORDER BY det.dia DESC
            """)
    Page<Object[]> buscarMisAsistencias(
            @Param("empleadoId") Long empleadoId,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin,
            Pageable pageable);
}
