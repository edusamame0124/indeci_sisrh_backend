package com.indeci.rrhh.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.rrhh.entity.EmpleadoEvento;

/**
 * F2.1 — Acceso a INDECI_EMPLEADO_EVENTO.
 *
 * <p>Queries clave:</p>
 * <ul>
 *   <li>{@link #findVigentesParaMotor} — usado por el motor PASO 3 para sumar
 *       días afectos. Trae los eventos del período activos del empleado y
 *       carga {@code tipoEvento} lazy para que el motor filtre por
 *       {@code afectaDiasLaborados}.</li>
 *   <li>{@link #findSolapados} — usado por el validador no-solape (F2.5)
 *       cuando se crea o edita un evento.</li>
 *   <li>{@link #findByEmpleadoIdAndActivoOrderByFechaInicioDesc} — timeline
 *       por empleado para la UI (F3).</li>
 * </ul>
 */
public interface EmpleadoEventoRepository
        extends JpaRepository<EmpleadoEvento, Long> {

    List<EmpleadoEvento> findByEmpleadoIdAndPeriodoAndActivo(
            Long empleadoId,
            String periodo,
            Integer activo);

    List<EmpleadoEvento> findByEmpleadoIdAndActivoOrderByFechaInicioDesc(
            Long empleadoId,
            Integer activo);

    /** F3.6 — bandeja operativa paginada con filtros opcionales. */
    @Query("""
            SELECT e
              FROM EmpleadoEvento e
             WHERE e.activo = 1
               AND (:empleadoId IS NULL OR e.empleadoId = :empleadoId)
               AND (:tipoEventoId IS NULL OR e.tipoEventoId = :tipoEventoId)
               AND (:estado IS NULL OR e.estado = :estado)
            """)
    Page<EmpleadoEvento> findBandejaPaginada(
            @Param("empleadoId") Long empleadoId,
            @Param("tipoEventoId") Long tipoEventoId,
            @Param("estado") String estado,
            Pageable pageable);

    /** F3.3 — eventos activos del período (preflight). */
    List<EmpleadoEvento> findByPeriodoAndActivo(String periodo, Integer activo);

    @Query("""
            SELECT e
              FROM EmpleadoEvento e
              JOIN e.tipoEvento t
             WHERE e.empleadoId          = :empleadoId
               AND e.activo              = 1
               AND e.estado              = 'VALIDADO'
               AND t.activo              = 1
               AND t.generaSubsidio      = 'S'
               AND (e.periodo = :periodo
                    OR (e.periodo IS NULL
                        AND e.fechaInicio <= :periodoFin
                        AND e.fechaFin    >= :periodoInicio))
            """)
    List<EmpleadoEvento> findSubsidiosParaMotor(
            @Param("empleadoId") Long empleadoId,
            @Param("periodo") String periodo,
            @Param("periodoInicio") LocalDate periodoInicio,
            @Param("periodoFin") LocalDate periodoFin);

    /**
     * F2.3 — Eventos del empleado en el período (activos) cuyo tipo afecta
     * días laborados. Devuelve también los que tengan PERIODO null pero cuyo
     * rango de fechas solape el período (uso defensivo cuando el campo
     * PERIODO no se haya seteado al crear el evento).
     */
    @Query("""
            SELECT e
              FROM EmpleadoEvento e
              JOIN e.tipoEvento t
             WHERE e.empleadoId            = :empleadoId
               AND e.activo                = 1
               AND t.activo                = 1
               AND t.afectaDiasLaborados   = 'S'
               AND (e.periodo = :periodo
                    OR (e.periodo IS NULL
                        AND e.fechaInicio <= :periodoFin
                        AND e.fechaFin    >= :periodoInicio))
            """)
    List<EmpleadoEvento> findVigentesParaMotor(
            @Param("empleadoId") Long empleadoId,
            @Param("periodo") String periodo,
            @Param("periodoInicio") LocalDate periodoInicio,
            @Param("periodoFin") LocalDate periodoFin);

    /**
     * F2.5 — Devuelve eventos activos del empleado cuyo rango de fechas se
     * traslapa con el rango propuesto. Usado por el validador no-solape al
     * crear/editar eventos. Excluye al evento que se está editando (id ≠ :idExcluir).
     */
    @Query("""
            SELECT e
              FROM EmpleadoEvento e
             WHERE e.empleadoId    = :empleadoId
               AND e.activo        = 1
               AND e.fechaInicio  <= :fechaFin
               AND e.fechaFin     >= :fechaInicio
               AND (:idExcluir IS NULL OR e.id <> :idExcluir)
            """)
    List<EmpleadoEvento> findSolapados(
            @Param("empleadoId") Long empleadoId,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin,
            @Param("idExcluir") Long idExcluir);

    /**
     * P0 subsidios - eventos previos de enfermedad dentro del anio calendario.
     * Trae eventos validados que solapan la ventana [anioInicio, diaPrevioEvento].
     */
    @Query("""
            SELECT e
              FROM EmpleadoEvento e
              JOIN e.tipoEvento t
             WHERE e.empleadoId = :empleadoId
               AND e.activo = 1
               AND e.estado = 'VALIDADO'
               AND t.activo = 1
               AND t.codigo = 'ENFERMEDAD'
               AND t.generaSubsidio = 'S'
               AND e.fechaInicio <= :hasta
               AND e.fechaFin >= :desde
               AND (:idExcluir IS NULL OR e.id <> :idExcluir)
            """)
    List<EmpleadoEvento> findEnfermedadesPreviasEnAnio(
            @Param("empleadoId") Long empleadoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("idExcluir") Long idExcluir);
}
