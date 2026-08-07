package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.SolicitudRrhh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SolicitudRrhhRepository
        extends JpaRepository<SolicitudRrhh, Long> {

    /**
     * Papeletas APROBADAS y activas del empleado que podrian cubrir el periodo (fecha de inicio
     * no posterior a {@code hasta}), candidatas para justificar/reclasificar un dia de
     * asistencia. El descarte fino por fecha exacta y por tipo especifico se hace en memoria
     * (cubreFecha, codigo) en {@code PapeletaJustificacionResolver}.
     *
     * <p>Incluye dos familias de tipo, sin mezclarlas en el significado de negocio:
     * <ul>
     *   <li>{@code t.justificaAsistencia = 1} — tipos "con goce" genericos que justifican
     *       cualquier FALTA que cubran (Teletrabajo, Licencia, etc.).</li>
     *   <li>{@code t.codigo in :codigosSiempreIncluidos} — tipos que el resolver reconoce por
     *       codigo especifico y aplican su PROPIA regla sin depender del flag generico
     *       (Vacaciones "012" sobrescribe LABORAL/TARDANZA; Omision "004" justifica
     *       OMISION_MARCACION). Estos NUNCA tienen el flag en 1 porque "tienen su propio tipo
     *       de dia" (V012_36) — sin este OR, la reconciliacion de vacaciones/omision nunca
     *       encontraria su propia papeleta.</li>
     * </ul>
     * Trae el tipo (join fetch) para conocer el codigo sin lazy loading extra.
     */
    @Query("select s from SolicitudRrhh s join fetch s.tipoSolicitud t "
            + "where s.empleadoId = :empleadoId "
            + "and s.activo = 1 "
            + "and s.estadoSolicitudId = :estadoAprobada "
            + "and (t.justificaAsistencia = 1 or t.codigo in :codigosSiempreIncluidos) "
            + "and s.fechaInicio is not null "
            + "and s.fechaInicio <= :hasta")
    List<SolicitudRrhh> findJustificantesAsistencia(
            @Param("empleadoId") Long empleadoId,
            @Param("estadoAprobada") Long estadoAprobada,
            @Param("hasta") LocalDate hasta,
            @Param("codigosSiempreIncluidos") List<String> codigosSiempreIncluidos);

    /**
     * Backfill — TODAS las papeletas activas APROBADAS cuyo tipo JUSTIFICA la asistencia
     * (flag {@code JUSTIFICA_ASISTENCIA}, REGLA-02: no hardcodeado por código), sin filtrar por
     * empleado. Trae el tipo (join fetch) para no requerir lazy loading extra.
     */
    @Query("select s from SolicitudRrhh s join fetch s.tipoSolicitud t "
            + "where s.activo = 1 "
            + "and s.estadoSolicitudId = :estadoAprobada "
            + "and t.justificaAsistencia = 1")
    List<SolicitudRrhh> findAprobadasQueJustificanAsistencia(
            @Param("estadoAprobada") Long estadoAprobada);

    /**
     * Backfill — papeletas activas APROBADAS cuyo tipo está en {@code codigos} (usado con
     * {@code CODIGOS_PERMISO_ASISTENCIA}, el mismo set que habilita la autorización manual de
     * tardanza en "Consulta diaria"), para poder autorizar automáticamente la tardanza que
     * cubren aunque su tipo no tenga el flag JUSTIFICA_ASISTENCIA (p.ej. "006" Comisión de
     * Servicio). Trae el tipo (join fetch) para no requerir lazy loading extra.
     */
    @Query("select s from SolicitudRrhh s join fetch s.tipoSolicitud t "
            + "where s.activo = 1 "
            + "and s.estadoSolicitudId = :estadoAprobada "
            + "and t.codigo in :codigos")
    List<SolicitudRrhh> findAprobadasPorTipoCodigoIn(
            @Param("estadoAprobada") Long estadoAprobada,
            @Param("codigos") List<String> codigos);

    List<SolicitudRrhh>
    findByEmpleadoIdAndActivo(
            Long empleadoId,
            Integer activo);

    List<SolicitudRrhh>
    findByActivo(Integer activo);
    
	boolean existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
	        Long empleadoId,
	        Long tipoSolicitudId,
	        Integer activo,
	        LocalDate fechaFin,
	        LocalDate fechaInicio
	);

	/**
	 * Variante del chequeo de duplicidad que excluye estados terminales negativos
	 * (RECHAZADO_JEFE, RECHAZADO_RRHH, ANULADO): una papeleta descartada no debe "reservar"
	 * el rango de fechas y bloquear que el empleado registre una nueva solicitud del mismo
	 * tipo (RR.HH. 2026-08-07).
	 */
	boolean existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndEstadoSolicitudIdNotInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
	        Long empleadoId,
	        Long tipoSolicitudId,
	        Integer activo,
	        List<Long> estadosExcluidos,
	        LocalDate fechaFin,
	        LocalDate fechaInicio
	);

	List<SolicitudRrhh>
	findByEmpleadoIdInAndActivo(
	        List<Long> empleadoIds,
	        Integer activo);

	/**
	 * Bandeja del Jefe Inmediato (SPEC — supervisión de papeletas): excluye los estados que
	 * todavía pertenecen al empleado ({@code BORRADOR}/{@code PENDIENTE_FIRMA}, antes de
	 * "Enviar"). El Jefe solo debe ver solicitudes ya enviadas con la papeleta firmada real,
	 * nunca un borrador en progreso que aún podría no llegar a enviarse.
	 */
	List<SolicitudRrhh>
	findByEmpleadoIdInAndActivoAndEstadoSolicitudIdNotIn(
	        List<Long> empleadoIds,
	        Integer activo,
	        List<Long> estadosExcluidos);
	
	
	List<SolicitudRrhh>
	findByEmpleadoIdAndTipoSolicitudIdAndEstadoSolicitudIdAndActivo(
	        Long empleadoId,
	        Long tipoSolicitudId,
	        Long estadoSolicitudId,
	        Integer activo);
	
	
	boolean existsByIdNotAndEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
	        Long id,
	        Long empleadoId,
	        Long tipoSolicitudId,
	        Integer activo,
	        LocalDate fechaFin,
	        LocalDate fechaInicio);

	/** Variante de {@link #existsByIdNotAndEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual}
	 * que excluye estados terminales negativos (ver duplicado de creación de arriba). */
	boolean existsByIdNotAndEmpleadoIdAndTipoSolicitudIdAndActivoAndEstadoSolicitudIdNotInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
	        Long id,
	        Long empleadoId,
	        Long tipoSolicitudId,
	        Integer activo,
	        List<Long> estadosExcluidos,
	        LocalDate fechaFin,
	        LocalDate fechaInicio);

    /**
     * Reporte de asistencia consolidado — suma en batch (sin N+1) las horas de papeletas
     * APROBADAS de los {@code codigos} indicados (p.ej. "001" Permiso por asuntos
     * personales) que se solapan con [desde, hasta], agrupadas por empleado.
     */
    @Query("select s.empleadoId as empleadoId, coalesce(sum(s.cantidadHoras), 0) as totalHoras "
            + "from SolicitudRrhh s join s.tipoSolicitud t "
            + "where s.activo = 1 and s.estadoSolicitudId = :estadoAprobada "
            + "and t.codigo in :codigos "
            + "and s.fechaInicio is not null and s.fechaInicio <= :hasta "
            + "and (s.fechaFin is null or s.fechaFin >= :desde) "
            + "group by s.empleadoId")
    List<HorasPermisoAgg> sumarHorasPorTipoEnRango(
            @Param("estadoAprobada") Long estadoAprobada,
            @Param("codigos") List<String> codigos,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /** Proyección de {@link #sumarHorasPorTipoEnRango}. */
    interface HorasPermisoAgg {
        Long getEmpleadoId();
        Double getTotalHoras();
    }
}