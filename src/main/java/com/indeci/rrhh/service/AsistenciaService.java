package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaDiariaEditDto;
import com.indeci.rrhh.dto.AsistenciaDiariaRowDto;
import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.dto.AsistenciaGuardarDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.Feriado;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.FeriadoRepository;
import com.indeci.rrhh.service.asistencia.EmpleadoJornadaResolver;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaResumenCalculator;
import com.indeci.rrhh.service.asistencia.AsistenciaTiempoUtil;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import com.indeci.rrhh.service.asistencia.CalendarioLaboralService;
import com.indeci.rrhh.service.asistencia.SalidaAnticipadaDescuentoCalculator;
import com.indeci.rrhh.service.asistencia.TardanzaDescuentoCalculator;

import java.math.BigDecimal;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Módulo M04 — Asistencia y Control de Tiempo (SPEC §12.2 PANTALLA-02).
 *
 * Captura el calendario mensual de cada empleado y calcula el descuento por
 * tardanzas y faltas según D.Leg. 276 Art. 24 (REGLA 276-02):
 *   descuento_tardanza = ROUND((remuneracion/30/8/60) * total_minutos, 2)
 *   descuento_falta    = ROUND((remuneracion/30) * dias_falta, 2)
 *
 * El motor M05 (PASO 7) consumirá estos valores cuando se integre.
 */
@Service
@RequiredArgsConstructor
public class AsistenciaService {

    private final AsistenciaCabeceraRepository cabeceraRepository;
    private final AsistenciaDetalleRepository detalleRepository;
    private final com.indeci.rrhh.service.asistencia.AsistenciaDetalleJdbcWriter detalleJdbcWriter;
    private final EmpleadoRepository empleadoRepository;
    private final PeriodoPlanillaRepository periodoPlanillaRepository;
    private final AuditoriaContext auditoriaContext;
    private final BaseAsistenciaResolver baseResolver;
    private final SolicitudRrhhRepository solicitudRrhhRepository;
    private final TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;
    private final com.indeci.rrhh.service.asistencia.PapeletaJustificacionResolver papeletaJustificacionResolver;

    private final EmpleadoJornadaResolver jornadaResolver;
    private final com.indeci.rrhh.service.asistencia.Turno24hReconciliadorService turno24hReconciliador;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final com.indeci.rrhh.repository.TeletrabajoReporteDetRepository teletrabajoReporteDetRepository;
    private final com.indeci.rrhh.repository.EmpleadoJornadaExcepcionRepository empleadoJornadaExcepcionRepository;
    private final FeriadoRepository feriadoRepository;
    private final AsistenciaImportacionFilaRepository importacionFilaRepository;
    private final CalendarioLaboralService calendarioLaboralService;

    /** ESTADO_SOLICITUD_ID = 9 → APROBADA. */
    private static final long ESTADO_SOLICITUD_APROBADA = 9L;

    /** Códigos de tipo de solicitud que cuentan como permiso/papeleta en asistencia. */
    private static final Set<String> CODIGOS_PERMISO_ASISTENCIA = Set.of(
            "001", "002", "003", "004", "005", "006", "008", "009", "010", "011", "012",
            // Comisión de servicio por día (V012_53) — mismo tratamiento que "006" (por horas).
            "COMISION_DIA");

    /** Tipos de día válidos (espejo del CHECK INDECI_ASIST_DET_TIPO_CK). */
    private static final Set<String> TIPOS_DIA = Set.of(
            "LABORAL", "FALTA", "TARDANZA", "LICENCIA", "VACACIONES", "DESCANSO",
            "FERIADO", "OBSERVADO", "SANCION_PAD", "TELETRABAJO", "PERMISO",
            "OMISION_MARCACION", "ASISTENCIA_JUSTIFICADA");

    /** Tipo de día que exige observación obligatoria (motivo/expediente PAD). */
    private static final String TIPO_DIA_SANCION_PAD = "SANCION_PAD";

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Set<String> ESTADOS_CABECERA = Set.of(
            "BORRADOR", "PREVALIDADA", "LISTA_PARA_VALIDAR", "OBSERVADA", "VALIDADA");

    /** Tipos que cuentan como día efectivamente laborado (TELETRABAJO = remoto, Ley 31572). */
    private static final Set<String> TIPOS_LABORADOS =
            Set.of("LABORAL", "TARDANZA", "TELETRABAJO", "ASISTENCIA_JUSTIFICADA");

    // ==========================================
    // CONSULTAR (empleado + período)
    // ==========================================

    /**
     * Devuelve la asistencia del empleado/período. Si aún no existe, retorna un
     * DTO con id null y lista de días vacía (calendario nuevo).
     */
    public AsistenciaResponseDto obtener(Long empleadoId, String periodo) {

        AsistenciaResponseDto dto = cabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .map(this::aResponse)
                .orElseGet(() -> {
                    AsistenciaResponseDto vacio = new AsistenciaResponseDto();
                    vacio.setEmpleadoId(empleadoId);
                    vacio.setPeriodo(periodo);
                    vacio.setEstado("BORRADOR");
                    vacio.setDiasLaborados(0);
                    vacio.setDiasFalta(0);
                    vacio.setTotalMinTardanza(0);
                    vacio.setDescuentoTardanza(0.0);
                    vacio.setDescuentoFalta(0.0);
                    vacio.setDias(new ArrayList<>());
                    return vacio;
                });

        // La remuneración base es de SOLO LECTURA en la UI: proviene de la configuración
        // del empleado (sueldo básico por régimen). Si el resolver la determina, se expone
        // como valor vigente para el cálculo del descuento (D.Leg. 276 Art. 24).
        double baseConfig = baseResolver.resolver(empleadoId).getRemuneracionBase();
        if (baseConfig > 0) {
            dto.setRemuneracionBase(baseConfig);
        }
        return dto;
    }

    // ==========================================
    // CONSULTA DIARIA (fecha + DNI opcional)
    // ==========================================

    /** Máximo rango consultable de una vez (evita consultas/tablas gigantes). */
    private static final long MAX_DIAS_RANGO = 92;

    @Transactional(readOnly = true)
    public Page<AsistenciaDiariaRowDto> listarDiaria(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String dni,
            String q,
            boolean soloHorarioEspecial,
            Pageable pageable) {
        if (fechaInicio == null) {
            throw new NegocioException("La fecha de inicio es obligatoria.");
        }
        // fechaFin opcional: si no viene, se consulta un solo día (compatibilidad).
        LocalDate fin = fechaFin != null ? fechaFin : fechaInicio;
        if (fin.isBefore(fechaInicio)) {
            throw new NegocioException("La fecha fin debe ser posterior o igual a la de inicio.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(fechaInicio, fin) > MAX_DIAS_RANGO) {
            throw new NegocioException("El rango no puede exceder " + MAX_DIAS_RANGO + " días. Redúzcalo.");
        }
        String dniFiltro = limpiarFiltro(dni);
        String qFiltro = limpiarFiltro(q);
        Page<AsistenciaDiariaRowDto> page = detalleRepository
                .buscarDiariaRango(fechaInicio, fin, dniFiltro, soloHorarioEspecial, qFiltro, pageable)
                .map(this::mapearDiariaRow);
        // Enriquecimiento por RANGO: cada fila se cruza con las papeletas/teletrabajo/horario
        // especial que cubren SU propia fecha (Opción A), no una fecha global.
        enriquecerPapeletas(page.getContent());
        enriquecerTeletrabajo(page.getContent());
        enriquecerHorarioEspecial(page.getContent());
        return page;
    }

    /**
     * Detalle diario de una IMPORTACIÓN (lote) — alimenta el módulo de detalle del historial de
     * importaciones (formato consulta diaria, de solo lectura). A diferencia de {@link
     * #listarDiaria}, abarca todos los días del período del lote, por lo que NO se hace el
     * enriquecimiento de papeletas/teletrabajo (es por fecha única); la fila conserva los campos de
     * papeleta que trae el propio detalle.
     */
    @Transactional(readOnly = true)
    public Page<AsistenciaDiariaRowDto> listarPorImportacion(
            Long importacionId,
            String dni,
            String q,
            String tipoDia,
            Pageable pageable) {
        if (importacionId == null) {
            throw new NegocioException("La importación es obligatoria.");
        }
        return detalleRepository
                .buscarPorImportacion(importacionId, limpiarFiltro(dni), limpiarFiltro(q),
                        limpiarFiltro(tipoDia), pageable)
                .map(this::mapearDiariaRow);
    }
    
    
    @Transactional(readOnly = true)
    public Page<AsistenciaDiariaRowDto> misAsistencias(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            Pageable pageable) {

        Long empleadoId = obtenerEmpleadoActual();

        Page<AsistenciaDiariaRowDto> page =
                detalleRepository
                        .buscarMisAsistencias(
                                empleadoId,
                                fechaInicio,
                                fechaFin,
                                pageable)
                        .map(this::mapearDiariaRow);

        return page;
    }
    
    /** Autoservicio: resuelve el empleado vinculado al usuario autenticado (usado también para exportar PDF propio). */
    public Long obtenerEmpleadoActual() {

        Long empleadoId = SecurityUtil.getEmpleadoId();

        if (empleadoId == null) {
            throw new NegocioException(
                    "El usuario no tiene un empleado vinculado.");
        }

        empleadoRepository.findById(empleadoId)
                .orElseThrow(() ->
                        new NegocioException(
                                "Empleado no encontrado"));

        return empleadoId;
    }

    /**
     * Cruza cada fila con INDECI_SOLICITUD_RRHH para marcar si el empleado tiene una
     * papeleta/permiso APROBADA (ESTADO_SOLICITUD_ID = 9) de un tipo de asistencia que
     * cubre la fecha del día. Expone tipo, motivo y el tiempo del permiso.
     */
    private void enriquecerPapeletas(List<AsistenciaDiariaRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> empleadoIds = rows.stream()
                .map(AsistenciaDiariaRowDto::getEmpleadoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (empleadoIds.isEmpty()) {
            return;
        }

        Map<Long, TipoSolicitudRrhh> tiposPermiso = tipoSolicitudRrhhRepository.findAll().stream()
                .filter(t -> t.getCodigo() != null && CODIGOS_PERMISO_ASISTENCIA.contains(t.getCodigo()))
                .collect(Collectors.toMap(TipoSolicitudRrhh::getId, t -> t));
        if (tiposPermiso.isEmpty()) {
            return;
        }

        // Papeletas aprobadas de tipo permiso, agrupadas por empleado (el match por fecha es
        // por fila, ya que la consulta abarca un rango de días).
        Map<Long, List<SolicitudRrhh>> papeletasPorEmpleado = new HashMap<>();
        for (SolicitudRrhh s : solicitudRrhhRepository.findByEmpleadoIdInAndActivo(empleadoIds, 1)) {
            if (s.getEstadoSolicitudId() == null
                    || s.getEstadoSolicitudId() != ESTADO_SOLICITUD_APROBADA) {
                continue;
            }
            if (s.getTipoSolicitudId() == null
                    || !tiposPermiso.containsKey(s.getTipoSolicitudId())) {
                continue;
            }
            papeletasPorEmpleado.computeIfAbsent(s.getEmpleadoId(), k -> new ArrayList<>()).add(s);
        }

        for (AsistenciaDiariaRowDto row : rows) {
            List<SolicitudRrhh> lista = papeletasPorEmpleado.get(row.getEmpleadoId());
            if (lista == null) {
                continue;
            }
            SolicitudRrhh s = lista.stream()
                    .filter(x -> cubreFecha(x, row.getFecha()))
                    .findFirst()
                    .orElse(null);
            if (s == null) {
                continue;
            }
            TipoSolicitudRrhh tipo = tiposPermiso.get(s.getTipoSolicitudId());
            row.setTienePapeletaAprobada(true);
            row.setPapeletaTipo(tipo != null ? tipo.getNombre() : null);
            row.setPapeletaMotivo(s.getMotivo());
            row.setPapeletaHoraInicio(s.getHoraInicio());
            row.setPapeletaHoraFin(s.getHoraFin());
            row.setPapeletaCantidadHoras(s.getCantidadHoras());
        }
    }

    /** El día cae dentro del rango [fechaInicio, fechaFin] del permiso (o el único día). */
    private boolean cubreFecha(SolicitudRrhh s, LocalDate fecha) {
        LocalDate ini = s.getFechaInicio();
        LocalDate fin = s.getFechaFin();
        if (ini == null) {
            return false;
        }
        if (fin == null) {
            return fecha.isEqual(ini);
        }
        return !fecha.isBefore(ini) && !fecha.isAfter(fin);
    }

    private void enriquecerTeletrabajo(List<AsistenciaDiariaRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> empleadoIds = rows.stream()
                .map(AsistenciaDiariaRowDto::getEmpleadoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (empleadoIds.isEmpty()) {
            return;
        }

        // Reportes de teletrabajo agrupados por empleado; el match por fecha es por fila (rango).
        Map<Long, List<com.indeci.rrhh.entity.TeletrabajoReporteDet>> porEmpleado = new HashMap<>();
        for (com.indeci.rrhh.entity.TeletrabajoReporteDet det
                : teletrabajoReporteDetRepository.findByEmpleadoIdInAndActivo(empleadoIds, 1)) {
            if (det.getFechaInicio() != null) {
                porEmpleado.computeIfAbsent(det.getEmpleadoId(), k -> new ArrayList<>()).add(det);
            }
        }

        for (AsistenciaDiariaRowDto row : rows) {
            List<com.indeci.rrhh.entity.TeletrabajoReporteDet> lista = porEmpleado.get(row.getEmpleadoId());
            if (lista == null) {
                continue;
            }
            LocalDate fecha = row.getFecha();
            boolean cubre = lista.stream().anyMatch(det -> {
                LocalDate ini = det.getFechaInicio();
                LocalDate fin = det.getFechaFin();
                return fin != null
                        ? !fecha.isBefore(ini) && !fecha.isAfter(fin)
                        : fecha.isEqual(ini);
            });
            if (cubre) {
                row.setTieneTeletrabajo(true);
            }
        }
    }

    /**
     * Marca cada fila con el Horario Especial vigente ese día (si lo hay), para el chip de
     * Consulta Diaria y el filtro "solo con horario especial vigente" — mismo patrón que
     * {@link #enriquecerPapeletas} / {@link #enriquecerTeletrabajo}: 1 sola consulta batch,
     * match por fila contra la fecha propia de esa fila (no una fecha global del rango).
     */
    private void enriquecerHorarioEspecial(List<AsistenciaDiariaRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> empleadoIds = rows.stream()
                .map(AsistenciaDiariaRowDto::getEmpleadoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (empleadoIds.isEmpty()) {
            return;
        }

        Map<Long, List<com.indeci.rrhh.entity.EmpleadoJornadaExcepcion>> excepcionesPorEmpleado =
                empleadoJornadaExcepcionRepository.findByEmpleadoIdInAndActivo(empleadoIds, 1).stream()
                        .collect(Collectors.groupingBy(com.indeci.rrhh.entity.EmpleadoJornadaExcepcion::getEmpleadoId));

        for (AsistenciaDiariaRowDto row : rows) {
            List<com.indeci.rrhh.entity.EmpleadoJornadaExcepcion> lista =
                    excepcionesPorEmpleado.get(row.getEmpleadoId());
            if (lista == null) {
                continue;
            }
            lista.stream()
                    .filter(exc -> exc.cubre(row.getFecha()))
                    .findFirst()
                    .ifPresent(exc -> {
                        row.setTieneHorarioEspecial(true);
                        row.setHorarioEspecialIngreso(exc.getHoraIngreso());
                        row.setHorarioEspecialSalida(exc.getHoraSalida());
                    });
        }
    }


    @Auditable(accion = "EDITAR_ASISTENCIA_DIA")
    @Transactional
    public AsistenciaDiariaRowDto editarDia(Long detalleId, AsistenciaDiariaEditDto dto) {
        AsistenciaDetalle det = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new NegocioException("Registro de asistencia no encontrado."));
        AsistenciaCabecera cab = cabeceraRepository.findById(det.getCabeceraId())
                .orElseThrow(() -> new NegocioException("Cabecera de asistencia no encontrada."));
        if (cab.getActivo() == null || cab.getActivo() != 1) {
            throw new NegocioException("No se puede editar una versión histórica de asistencia.");
        }
        validarPeriodoEditable(cab.getPeriodo());

        Optional<SolicitudRrhh> papeleta = buscarPapeletaAprobada(cab.getEmpleadoId(), det.getDia());
        boolean autorizacionAplicada = false;
        if (papeleta.isPresent()) {
            autorizacionAplicada = aplicarDecisionPapeleta(det, dto, papeleta.get());
        } else if (dto.getTipoDia() != null) {
            if (!TIPOS_DIA.contains(dto.getTipoDia())) {
                throw new NegocioException("Tipo de día inválido: " + dto.getTipoDia());
            }
            if (TIPO_DIA_SANCION_PAD.equals(dto.getTipoDia())
                    && (dto.getObservacion() == null || dto.getObservacion().isBlank())) {
                throw new NegocioException(
                        "Debe indicar el motivo/expediente PAD para marcar el día como Sanción PAD.");
            }
            det.setTipoDia(dto.getTipoDia());
        }
        if (dto.getMarcaEntrada() != null) {
            det.setMarcaEntrada(dto.getMarcaEntrada().isBlank() ? null : dto.getMarcaEntrada().trim());
        }
        if (dto.getMarcaSalida() != null) {
            det.setMarcaSalida(dto.getMarcaSalida().isBlank() ? null : dto.getMarcaSalida().trim());
        }
        // Si se acaba de autorizar la papeleta, MINUTOS_TARDANZA/OBSERVACION ya quedaron al día
        // dentro de aplicarDecisionPapeleta (Punto 1); no reaplicar aquí valores del form que
        // podrían venir stale (p.ej. el minutosTardanza previo a la marcación real).
        if (!autorizacionAplicada) {
            if (dto.getMinutosTardanza() != null) {
                det.setMinutosTardanza(Math.max(0, dto.getMinutosTardanza()));
            }
            if (dto.getObservacion() != null) {
                det.setObservacion(dto.getObservacion().isBlank() ? null : dto.getObservacion().trim());
            }
        }
        det.setOrigen("MANUAL");
        detalleRepository.save(det);

        recalcularCabeceraDesdeDetalle(cab);

        auditoriaContext.setDetalle(
                "Asistencia diaria editada detalle " + detalleId
                        + " empleado " + cab.getEmpleadoId()
                        + " fecha " + det.getDia());

        AsistenciaDiariaRowDto row = construirDiariaRowDto(det, cab);
        // La fila justificable por su propia fecha (nuevo enriquecimiento por rango).
        enriquecerPapeletas(List.of(row));
        return row;
    }

    /**
     * @return true si la papeleta quedó AUTORIZADA (Punto 1: en ese caso ya deja
     *         MINUTOS_TARDANZA/OBSERVACION al día — el llamador no debe reaplicar los valores
     *         del formulario encima).
     */
    private boolean aplicarDecisionPapeleta(
            AsistenciaDetalle det, AsistenciaDiariaEditDto dto, SolicitudRrhh papeleta) {
        if (dto.getPapeletaAutorizada() == null) {
            throw new NegocioException(
                    "Debe indicar si autoriza o no autoriza la papeleta del día.");
        }
        boolean autorizada = dto.getPapeletaAutorizada();
        det.setPapeletaAutorizada(autorizada ? 1 : 0);
        det.setPapeletaDecisionUsuario(obtenerUsuarioDecision());
        det.setPapeletaDecisionFecha(LocalDateTime.now());
        if (autorizada) {
            det.setTipoDia("LABORAL");
            det.setPapeletaMotivoRechazo(null);
            // Bug reportado: PAPELETA_AUTORIZADA quedaba en 1 pero MINUTOS_TARDANZA/OBSERVACION
            // seguían mostrando el valor de la marcación física original (ej. "289 min" /
            // "NO AUTORIZADO"), porque antes esto no se tocaba al autorizar.
            det.setMinutosTardanza(0);
            det.setObservacion(observacionAutorizacionPapeleta(papeleta));
        } else {
            String motivo = dto.getPapeletaMotivoRechazo();
            if (motivo == null || motivo.isBlank()) {
                throw new NegocioException(
                        "Debe señalar el motivo de no autorización de la papeleta.");
            }
            String motivoTrim = motivo.trim();
            if (motivoTrim.length() > 500) {
                throw new NegocioException(
                        "El motivo de no autorización no puede exceder 500 caracteres.");
            }
            det.setTipoDia("OBSERVADO");
            det.setPapeletaMotivoRechazo(motivoTrim);
        }
        return autorizada;
    }

    private Optional<SolicitudRrhh> buscarPapeletaAprobada(Long empleadoId, LocalDate fecha) {
        if (empleadoId == null || fecha == null) {
            return Optional.empty();
        }
        Map<Long, TipoSolicitudRrhh> tiposPermiso = tipoSolicitudRrhhRepository.findAll().stream()
                .filter(t -> t.getCodigo() != null && CODIGOS_PERMISO_ASISTENCIA.contains(t.getCodigo()))
                .collect(Collectors.toMap(TipoSolicitudRrhh::getId, t -> t));
        if (tiposPermiso.isEmpty()) {
            return Optional.empty();
        }
        for (SolicitudRrhh s : solicitudRrhhRepository.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)) {
            if (s.getEstadoSolicitudId() == null
                    || s.getEstadoSolicitudId() != ESTADO_SOLICITUD_APROBADA) {
                continue;
            }
            if (s.getTipoSolicitudId() == null
                    || !tiposPermiso.containsKey(s.getTipoSolicitudId())) {
                continue;
            }
            if (!cubreFecha(s, fecha)) {
                continue;
            }
            return Optional.of(s);
        }
        return Optional.empty();
    }

    private String obtenerUsuarioDecision() {
        try {
            String usuario = SecurityUtil.getUsername();
            if (usuario != null && !usuario.isBlank()) {
                return usuario;
            }
        } catch (Exception ignored) {
            // Sin contexto de seguridad (tests u operación interna).
        }
        return "SISTEMA";
    }

    void validarPeriodoEditable(String periodo) {
        periodoPlanillaRepository.findByPeriodoAndActivo(periodo, 1)
                .ifPresent(p -> {
                    String estado = p.getEstado();
                    if ("CERRADO".equals(estado) || "APROBADO".equals(estado)) {
                        throw new NegocioException(
                                "No se puede modificar la asistencia: el periodo "
                                        + periodo + " está " + estado.toLowerCase() + ".");
                    }
                });
    }

    private void recalcularCabeceraDesdeDetalle(AsistenciaCabecera cab) {
        List<AsistenciaDiaDto> dias = detalleRepository.findByCabeceraIdOrderByDia(cab.getId())
                .stream()
                .map(this::aDiaDto)
                .toList();
        double remun = cab.getRemuneracionBase() != null ? cab.getRemuneracionBase() : 0.0;
        AsistenciaResumenCalculator.Resumen agregados =
                AsistenciaResumenCalculator.calcular(dias, remun);
        cab.setDiasLaborados(agregados.getDiasLaborados());
        cab.setDiasFalta(agregados.getDiasFalta());
        cab.setMinutosSalidaAnticipada(agregados.getMinutosSalidaAnticipada());
        cab.setMarcasIncompletas(agregados.getMarcasIncompletas());
        cab.setDescuentoFalta(agregados.getDescuentoFalta());

        aplicarDescuentoTardanzaDosNiveles(cab, dias, remun);
        aplicarDescuentoSalidaAnticipadaDosNiveles(cab, dias, remun);
        cabeceraRepository.save(cab);
    }

    /** Código del tipo "Solicitud de Vacaciones" (INDECI_TIPO_SOLICITUD_RRHH). */
    private static final String CODIGO_VACACIONES = "012";

    /**
     * Al aprobar una papeleta, reconcilia los días ya importados dentro de
     * [fechaInicio, fechaFin] que hubieran quedado en FALTA/OMISION_MARCACION/LABORAL/TARDANZA
     * porque la carga de asistencia ocurrió ANTES de que RR. HH. aprobara la papeleta. Sin esto
     * el día queda congelado para siempre, aunque la papeleta ya esté aprobada (bug real:
     * solicitud Nº376, teletrabajo aprobado el 31/07 para el 24/07 ya importado).
     *
     * <p>Tolerante: no reconcilia periodos con planilla CERRADA/APROBADA (LEY-05, inmutables).
     * Para tipos distintos de Vacaciones, tampoco actúa sobre meses sin cabecera de asistencia
     * cargada (nada que reconciliar). Vacaciones es la excepción (Fase A): si el mes no tiene
     * cabecera, la crea desde cero — ver {@link #crearCabeceraVacaciones}.
     *
     * @return los períodos (formato "yyyy-MM") en los que la papeleta NO tuvo efecto porque su
     *     planilla ya está CERRADA/APROBADA — vacío si reconcilió todo o si no había nada que
     *     reconciliar. Lo consume {@code SolicitudRrhhService.aprobarRrhh} para devolver una
     *     advertencia a RR.HH. (P3, 2026-08-07): sin esto, la papeleta queda aprobada en
     *     silencio pero sin ningún efecto sobre una asistencia/planilla ya cerrada.
     */
    @Transactional
    public List<String> reconciliarPorPapeletaAprobada(SolicitudRrhh solicitud, TipoSolicitudRrhh tipo) {
        if (tipo == null) {
            return List.of();
        }
        boolean esVacaciones = CODIGO_VACACIONES.equals(tipo.getCodigo());
        boolean justificaFalta = Integer.valueOf(1).equals(tipo.getJustificaAsistencia());
        // Punto 2 (decisión RR.HH.): una papeleta de tipo "permiso" (el mismo set que habilita
        // la autorización manual en Consulta diaria, p.ej. "006" Comisión de Servicio) ya
        // aprobada por RR.HH. autoriza automáticamente la TARDANZA que cubre, sin exigir que
        // RR.HH. repita la decisión día por día en otra pantalla.
        boolean autorizaTardanza = tipo.getCodigo() != null
                && CODIGOS_PERMISO_ASISTENCIA.contains(tipo.getCodigo());
        if (!justificaFalta && !autorizaTardanza && !esVacaciones) {
            return List.of();
        }
        Long empleadoId = solicitud.getEmpleadoId();
        LocalDate ini = solicitud.getFechaInicio();
        LocalDate fin = solicitud.getFechaFin() != null ? solicitud.getFechaFin() : ini;
        if (empleadoId == null || ini == null) {
            return List.of();
        }
        // Vacaciones también necesita justificantes (para justificar() en FALTA y
        // justificarVacacionSobreMarcacion() en LABORAL/TARDANZA) aunque su tipo nunca tenga
        // JUSTIFICA_ASISTENCIA=1 — tiene su propio código reconocido por el resolver.
        List<SolicitudRrhh> justificantes = (justificaFalta || esVacaciones)
                ? papeletaJustificacionResolver.cargarJustificantes(empleadoId, fin)
                : List.of();
        SolicitudRrhh papeletaAutorizacion = autorizaTardanza ? solicitud : null;
        if (justificantes.isEmpty() && papeletaAutorizacion == null && !esVacaciones) {
            return List.of();
        }
        List<String> periodosBloqueados = new ArrayList<>();
        for (String periodo : periodosEntre(ini, fin)) {
            Optional<AsistenciaCabecera> cab =
                    cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1);
            if (cab.isPresent()) {
                reconciliarDetalleCabecera(cab.get(), ini, fin, justificantes, papeletaAutorizacion)
                        .ifPresent(periodosBloqueados::add);
            } else if (esVacaciones) {
                // Fase A (decisión RR.HH.): la vacación no depende de que exista una marcación
                // física para "existir" en el calendario. Si nunca se importó un marcador para
                // este período (nada que reconciliar), se crea la cabecera desde cero con los
                // días de la papeleta ya en VACACIONES — así Mis Asistencias, Asistencia por
                // empleado y Consulta por rango leen el MISMO dato real en vez de cada una
                // improvisar (Sin registro / Presente por defecto / Sin registros).
                crearCabeceraVacaciones(empleadoId, periodo, ini, fin, solicitud);
            }
        }
        return periodosBloqueados;
    }

    /**
     * Crea la cabecera + detalle de un período que aún no tiene ninguna carga de asistencia, a
     * partir de una papeleta de VACACIONES ya APROBADA que lo cubre. Solo genera detalle para
     * los días de {@code [ini,fin] ∩ período}: laborables → VACACIONES, fin de semana →
     * DESCANSO, feriado → FERIADO. El resto del período (fuera del rango de la papeleta) queda
     * SIN REGISTRO, igual que antes — no se inventa dato para lo que no se sabe.
     *
     * <p>Si más adelante se importa un marcador real para el mismo período,
     * {@link #guardarImportacion} versiona esta cabecera normalmente (ACTIVO=0) y
     * {@code sobrescribirVacacionesSobreMarcacion} vuelve a aplicar la reclasificación sobre la
     * nueva — esta cabecera "de origen papeleta" nunca es un obstáculo para una importación real
     * posterior.
     */
    private void crearCabeceraVacaciones(
            Long empleadoId, String periodo, LocalDate ini, LocalDate fin, SolicitudRrhh solicitud) {
        boolean periodoBloqueado = periodoPlanillaRepository.findByPeriodoAndActivo(periodo, 1)
                .map(p -> "CERRADO".equals(p.getEstado()) || "APROBADO".equals(p.getEstado()))
                .orElse(false);
        if (periodoBloqueado) {
            return;
        }

        YearMonth ym = YearMonth.parse(periodo);
        LocalDate desdePeriodo = ym.atDay(1);
        LocalDate hastaPeriodo = ym.atEndOfMonth();
        LocalDate desde = ini.isAfter(desdePeriodo) ? ini : desdePeriodo;
        LocalDate hasta = fin.isBefore(hastaPeriodo) ? fin : hastaPeriodo;
        if (desde.isAfter(hasta)) {
            return;
        }

        Long regimenId = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(EmpleadoPlanilla::getRegimenLaboralId)
                .orElse(null);
        CalendarioLaboralService.Calendario calendario = calendarioLaboralService.paraPeriodo(desde, hasta);
        String observacion = observacionVacacionRegistrada(solicitud);

        List<AsistenciaDiaDto> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            AsistenciaDiaDto dto = new AsistenciaDiaDto();
            dto.setDia(dia);
            if (calendario.esFeriado(dia)) {
                dto.setTipoDia("FERIADO");
            } else if (calendario.esDescanso(dia, regimenId)) {
                dto.setTipoDia("DESCANSO");
            } else {
                dto.setTipoDia("VACACIONES");
                dto.setObservacion(observacion);
                dto.setOrigen("PAPELETA");
            }
            dto.setMinutosTardanza(0);
            dias.add(dto);
        }
        if (dias.isEmpty()) {
            return;
        }

        double remun = baseResolver.resolver(empleadoId).getRemuneracionBase();
        AsistenciaResumenCalculator.Resumen agregados = AsistenciaResumenCalculator.calcular(dias, remun);

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEmpleadoId(empleadoId);
        cab.setPeriodo(periodo);
        cab.setActivo(1);
        cab.setVersion(1);
        cab.setCreatedAt(LocalDateTime.now());
        cab.setRemuneracionBase(remun);
        cab.setDiasLaborados(agregados.getDiasLaborados());
        cab.setDiasFalta(agregados.getDiasFalta());
        cab.setTotalMinTardanza(agregados.getTotalMinTardanza());
        cab.setDescuentoTardanza(agregados.getDescuentoTardanza());
        cab.setDescuentoFalta(agregados.getDescuentoFalta());
        cab.setMinutosSalidaAnticipada(agregados.getMinutosSalidaAnticipada());
        cab.setMarcasIncompletas(agregados.getMarcasIncompletas());
        cab.setEstado("BORRADOR");
        cab.setObservacion("Generada automáticamente por papeleta de Vacaciones N°" + solicitud.getId() + " aprobada.");

        AsistenciaCabecera guardada = cabeceraRepository.save(cab);
        detalleJdbcWriter.insertarLote(mapearDetalles(guardada.getId(), dias));
    }

    private String observacionVacacionRegistrada(SolicitudRrhh solicitud) {
        StringBuilder sb = new StringBuilder("Vacaciones registradas por papeleta aprobada");
        if (solicitud.getId() != null) {
            sb.append(" N°").append(solicitud.getId());
        }
        if (solicitud.getFechaAprobacion() != null) {
            sb.append(" (aprobada ")
                    .append(solicitud.getFechaAprobacion().toLocalDate().format(FECHA_CORTA))
                    .append(")");
        }
        return sb.append('.').toString();
    }

    /**
     * Backfill ÚNICO — NO se ejecuta automáticamente. Reconcilia TODAS las papeletas
     * (cualquier tipo que JUSTIFICA la asistencia o que autoriza tardanza, flag/código
     * dinámico — REGLA-02) ya APROBADAS contra las cabeceras de asistencia activas actuales,
     * con la misma regla de {@link #reconciliarPorPapeletaAprobada} (incluye el override
     * LABORAL/TARDANZA → VACACIONES sobre marcación física real, solo para Vacaciones, y la
     * autorización automática de TARDANZA para papeletas tipo "permiso" — decisión RR.HH.).
     * Sanea el histórico que quedó desincronizado antes de este fix (casos reales: AGUILAR
     * MORENO EDER AARON, CASTILLO BENAVENTE PERSIBAL JESUS, BALTAZAR FLORES MELINA JHESENIA,
     * TINEO PONGO PERCY).
     *
     * <p>Idempotente: cada papeleta pasa por el mismo camino que al aprobarse, así que
     * correrlo dos veces no produce cambios adicionales tras la primera pasada exitosa —
     * seguro de reintentar si se corta a la mitad.
     */
    @Transactional
    public int backfillReconciliarVacacionesAprobadas() {
        List<SolicitudRrhh> aprobadas = new ArrayList<>(solicitudRrhhRepository
                .findAprobadasQueJustificanAsistencia(ESTADO_SOLICITUD_APROBADA));
        Set<Long> ids = aprobadas.stream().map(SolicitudRrhh::getId).collect(Collectors.toSet());
        for (SolicitudRrhh s : solicitudRrhhRepository.findAprobadasPorTipoCodigoIn(
                ESTADO_SOLICITUD_APROBADA, new ArrayList<>(CODIGOS_PERMISO_ASISTENCIA))) {
            if (ids.add(s.getId())) {
                aprobadas.add(s);
            }
        }
        int procesadas = 0;
        for (SolicitudRrhh s : aprobadas) {
            reconciliarPorPapeletaAprobada(s, s.getTipoSolicitud());
            procesadas++;
        }
        return procesadas;
    }

    /**
     * Backfill ÚNICO (independiente del de papeletas) — NO se ejecuta automáticamente. Corrige
     * días ya persistidos que quedaron mal clasificados como {@code LABORAL}/{@code OBSERVADO}
     * pero que en realidad son {@code FERIADO} según el catálogo oficial ({@code INDECI_FERIADO})
     * y no tienen marcación real (no es "feriado trabajado"). Causa: antes de este fix,
     * {@code AsistenciaMarcadorMapper} solo reconocía "jueves/viernes santo" por texto del
     * marcador — Fiestas Patrias, Día de la Fuerza Aérea, etc. caían al LABORAL por defecto.
     *
     * <p>Solo toca cabeceras ACTIVAS y respeta periodos CERRADO/APROBADO (inmutables, LEY-05).
     * Idempotente: un día ya corregido a FERIADO no vuelve a matchear la condición.
     */
    @Transactional
    public int backfillFeriadosMalClasificados() {
        List<AsistenciaCabecera> activas = cabeceraRepository.findByActivo(1);
        if (activas.isEmpty()) {
            return 0;
        }

        Set<Integer> anios = new java.util.HashSet<>();
        for (AsistenciaCabecera cab : activas) {
            Integer anio = anioDePeriodo(cab.getPeriodo());
            if (anio != null) {
                anios.add(anio);
            }
        }
        if (anios.isEmpty()) {
            return 0;
        }

        Set<LocalDate> feriados = new java.util.HashSet<>();
        for (Feriado f : feriadoRepository.findByAnioInAndActivo(anios, 1)) {
            if (f.getFecha() != null) {
                feriados.add(f.getFecha());
            }
        }
        if (feriados.isEmpty()) {
            return 0;
        }

        int corregidos = 0;
        for (AsistenciaCabecera cab : activas) {
            boolean periodoBloqueado = periodoPlanillaRepository.findByPeriodoAndActivo(cab.getPeriodo(), 1)
                    .map(p -> "CERRADO".equals(p.getEstado()) || "APROBADO".equals(p.getEstado()))
                    .orElse(false);
            if (periodoBloqueado) {
                continue;
            }

            boolean huboCambios = false;
            for (AsistenciaDetalle det : detalleRepository.findByCabeceraIdOrderByDia(cab.getId())) {
                if (det.getDia() == null || !feriados.contains(det.getDia())) {
                    continue;
                }
                boolean tipoCorregible =
                        "LABORAL".equals(det.getTipoDia()) || "OBSERVADO".equals(det.getTipoDia());
                boolean sinMarcacionReal = !AsistenciaTiempoUtil.tieneMarca(det.getMarcaEntrada())
                        && !AsistenciaTiempoUtil.tieneMarca(det.getMarcaSalida());
                if (tipoCorregible && sinMarcacionReal) {
                    det.setTipoDia("FERIADO");
                    detalleRepository.save(det);
                    corregidos++;
                    huboCambios = true;
                }
            }
            if (huboCambios) {
                recalcularCabeceraDesdeDetalle(cab);
            }
        }
        return corregidos;
    }

    /**
     * Backfill ÚNICO (independiente de los anteriores) — NO se ejecuta automáticamente.
     * Corrige cabeceras ACTIVAS cuyo detalle no cubre todo lo que ya se había cargado porque
     * una re-importación anterior a la fusión de días (fix F5/P4 en {@link #guardarImportacion})
     * dejó días huérfanos en una versión inactiva anterior del mismo empleado+periodo. Inserta
     * esos días faltantes en la cabecera activa actual y recalcula sus agregados con
     * {@link #recalcularCabeceraDesdeDetalle} — misma fuente de verdad que usa el fix.
     *
     * <p>Respeta el mismo guard que {@link #backfillFeriadosMalClasificados}: no toca periodos
     * CERRADO/APROBADO (inmutables, LEY-05) — esos casos quedan para revisión manual de RR.HH.
     *
     * <p>Idempotente: un día ya presente en el detalle activo no se duplica.
     */
    @Transactional
    public int backfillFusionarDiasHuerfanos() {
        List<AsistenciaCabecera> activas = cabeceraRepository.findByActivo(1);
        int corregidas = 0;

        for (AsistenciaCabecera cab : activas) {
            boolean periodoBloqueado = periodoPlanillaRepository.findByPeriodoAndActivo(cab.getPeriodo(), 1)
                    .map(p -> "CERRADO".equals(p.getEstado()) || "APROBADO".equals(p.getEstado()))
                    .orElse(false);
            if (periodoBloqueado) {
                continue;
            }

            Set<LocalDate> diasActivos = detalleRepository.findByCabeceraIdOrderByDia(cab.getId()).stream()
                    .map(AsistenciaDetalle::getDia)
                    .collect(Collectors.toSet());

            List<AsistenciaDetalle> faltantes = new ArrayList<>();
            for (AsistenciaCabecera version : cabeceraRepository
                    .findByEmpleadoIdAndPeriodoOrderByVersionDesc(cab.getEmpleadoId(), cab.getPeriodo())) {
                if (version.getId().equals(cab.getId()) || version.getActivo() == 1) {
                    continue; // solo versiones inactivas anteriores
                }
                for (AsistenciaDetalle det : detalleRepository.findByCabeceraIdOrderByDia(version.getId())) {
                    if (det.getDia() != null && diasActivos.add(det.getDia())) {
                        faltantes.add(mapearDetalles(cab.getId(), List.of(aDiaDto(det))).get(0));
                    }
                }
            }

            if (!faltantes.isEmpty()) {
                detalleJdbcWriter.insertarLote(faltantes);
                recalcularCabeceraDesdeDetalle(cab);
                corregidas++;
            }
        }
        return corregidas;
    }

    /**
     * Backfill ÚNICO (independiente de los anteriores) — NO se ejecuta automáticamente.
     * Corrige días ya persistidos que quedaron mal clasificados como {@code LABORAL}
     * ("Presente") porque {@code AsistenciaCsvParser} nunca llenaba
     * {@code MarcadorCsvRow.salidaAnticipada} (bug corregido 2026-08-07): el dato crudo de la
     * columna "T/AS" del marcador SÍ se guardó en
     * {@code INDECI_ASISTENCIA_IMPORTACION_FILA.TIEMPO_ANTES_SAL_MIN}, pero nunca llegó al
     * detalle final ({@code MINUTOS_SALIDA_ANTICIPADA} quedaba en 0 siempre).
     *
     * <p>Defensivo por diseño (decisión RR.HH. 2026-08-07): solo toca un día si su
     * {@code TIPO_DIA} sigue EXACTAMENTE en {@code LABORAL} — el síntoma preciso del bug.
     * Nunca pisa {@code VACACIONES}, {@code LICENCIA}, {@code TARDANZA}, {@code OMISION_MARCACION}
     * ni ningún día ya reclasificado por reconciliación o por edición manual de RR.HH.: si el
     * día cambió de estado por cualquier otra razón, ya no es candidato y este backfill lo deja
     * intacto.
     *
     * <p>Respeta el mismo guard que los backfills hermanos: no toca periodos CERRADO/APROBADO
     * (inmutables, LEY-05). Idempotente: un día ya corregido pasa a OBSERVADO y deja de ser
     * LABORAL, así que no vuelve a matchear en una segunda corrida.
     */
    @Transactional
    public int backfillSalidaAnticipada() {
        List<AsistenciaCabecera> activas = cabeceraRepository.findByActivo(1);
        int corregidos = 0;

        for (AsistenciaCabecera cab : activas) {
            if (cab.getEmpleadoId() == null) {
                continue;
            }
            boolean periodoBloqueado = periodoPlanillaRepository.findByPeriodoAndActivo(cab.getPeriodo(), 1)
                    .map(p -> "CERRADO".equals(p.getEstado()) || "APROBADO".equals(p.getEstado()))
                    .orElse(false);
            if (periodoBloqueado) {
                continue;
            }

            boolean huboCambios = false;
            for (AsistenciaDetalle det : detalleRepository.findByCabeceraIdOrderByDia(cab.getId())) {
                if (!"LABORAL".equals(det.getTipoDia()) || det.getDia() == null) {
                    continue; // solo el síntoma exacto del bug — nunca pisa otro estado
                }
                int minutosReales = importacionFilaRepository
                        .findByEmpleadoIdAndFecha(cab.getEmpleadoId(), det.getDia())
                        .stream()
                        .map(AsistenciaImportacionFila::getTiempoAntesSalMin)
                        .filter(Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(0);
                if (minutosReales <= 0) {
                    continue;
                }
                det.setTipoDia("OBSERVADO");
                det.setMinutosSalidaAnticipada(minutosReales);
                String obsPrevia = det.getObservacion();
                String obsBackfill = "Actualizado por backfill de salida anticipada: el dato del "
                        + "marcador no llegaba al detalle final por un bug de import corregido el "
                        + "2026-08-07.";
                det.setObservacion(
                        obsPrevia == null || obsPrevia.isBlank() ? obsBackfill : obsPrevia + " | " + obsBackfill);
                detalleRepository.save(det);
                corregidos++;
                huboCambios = true;
            }
            if (huboCambios) {
                recalcularCabeceraDesdeDetalle(cab);
            }
        }
        return corregidos;
    }

    /**
     * Backfill ÚNICO (temporal, independiente de los anteriores) — corrige días ya
     * persistidos como FALTA de guardias COEN 24h (08:30→08:30 del día siguiente) para
     * empleados con turno 24h activo, aplicando el mismo reconciliador que corre en cada
     * import nuevo ({@link com.indeci.rrhh.service.asistencia.Turno24hReconciliadorService}).
     * Útil para meses ya cargados ANTES de que el trabajador tuviera su turno 24h registrado
     * en el sistema, o antes de que este fix existiera (RIS INDECI 2026-08-09).
     *
     * <p>Respeta el mismo guard que los backfills hermanos: no toca periodos CERRADO/APROBADO
     * (inmutables, LEY-05) — esos quedan omitidos silenciosamente, nunca bloquean el resto.
     * Idempotente: un día ya reconciliado deja de ser FALTA, así que no vuelve a matchear en
     * una segunda corrida.
     *
     * @return cantidad de días (detalle) reclasificados en total.
     */
    @Transactional
    public int backfillTurno24h() {
        List<AsistenciaCabecera> activas = cabeceraRepository.findByActivo(1);
        int corregidos = 0;

        for (AsistenciaCabecera cab : activas) {
            if (cab.getEmpleadoId() == null) {
                continue;
            }
            boolean periodoBloqueado = periodoPlanillaRepository.findByPeriodoAndActivo(cab.getPeriodo(), 1)
                    .map(p -> "CERRADO".equals(p.getEstado()) || "APROBADO".equals(p.getEstado()))
                    .orElse(false);
            if (periodoBloqueado) {
                continue;
            }

            int reconciliados = turno24hReconciliador.reconciliar(cab);
            if (reconciliados > 0) {
                recalcularCabeceraDesdeDetalle(cab);
                corregidos += reconciliados;
            }
        }
        return corregidos;
    }

    /** Año (yyyy) del período "yyyy-MM"; null si el formato no es el esperado. */
    private Integer anioDePeriodo(String periodo) {
        if (periodo == null || periodo.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(periodo.substring(0, 4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * @return el período (cab.getPeriodo()) si estaba CERRADO/APROBADO y por eso no se
     *     reconcilió nada; vacío en cualquier otro caso (reconcilió, o no había nada que
     *     reconciliar).
     */
    private Optional<String> reconciliarDetalleCabecera(
            AsistenciaCabecera cab, LocalDate ini, LocalDate fin,
            List<SolicitudRrhh> justificantes, SolicitudRrhh papeletaAutorizacion) {
        boolean periodoBloqueado = periodoPlanillaRepository.findByPeriodoAndActivo(cab.getPeriodo(), 1)
                .map(p -> "CERRADO".equals(p.getEstado()) || "APROBADO".equals(p.getEstado()))
                .orElse(false);
        if (periodoBloqueado) {
            return Optional.of(cab.getPeriodo());
        }
        boolean huboCambios = false;
        for (AsistenciaDetalle det : detalleRepository.findByCabeceraIdOrderByDia(cab.getId())) {
            LocalDate dia = det.getDia();
            if (dia == null || dia.isBefore(ini) || dia.isAfter(fin)) {
                continue;
            }
            String tipoActual = det.getTipoDia();
            Optional<AsistenciaDiaDto> justificado;
            if ("OMISION_MARCACION".equals(tipoActual)) {
                justificado = papeletaJustificacionResolver.justificarOmision(dia, justificantes);
            } else if ("FALTA".equals(tipoActual)) {
                justificado = papeletaJustificacionResolver.justificar(dia, justificantes);
            } else if ("LABORAL".equals(tipoActual) || "TARDANZA".equals(tipoActual)) {
                // Decisión RR.HH.: una papeleta de VACACIONES aprobada manda sobre una marcación
                // física real (el trabajador no debía estar laborando ese día). No aplica a
                // Teletrabajo/Permiso, que no tienen definida esta regla de sobrescritura.
                justificado = papeletaJustificacionResolver.justificarVacacionSobreMarcacion(dia, justificantes);
            } else if ("OBSERVADO".equals(tipoActual)
                    && det.getMinutosSalidaAnticipada() != null && det.getMinutosSalidaAnticipada() > 0) {
                // Decisión RR.HH. 2026-08-07: una papeleta de permiso (con o sin goce) aprobada
                // que cubre la ventana horaria de la salida anticipada limpia los minutos y
                // devuelve el día a LABORAL ("Presente") — no a PERMISO, para no mezclarlo con
                // la condición de permiso de día completo.
                justificado = papeletaJustificacionResolver.justificarSalidaAnticipada(dia, justificantes);
            } else {
                justificado = Optional.empty();
            }
            if (justificado.isPresent()) {
                AsistenciaDiaDto dto = justificado.get();
                det.setTipoDia(dto.getTipoDia());
                det.setMinutosTardanza(dto.getMinutosTardanza());
                det.setMinutosSalidaAnticipada(dto.getMinutosSalidaAnticipada());
                det.setObservacion(dto.getObservacion());
                det.setOrigen(dto.getOrigen());
                detalleRepository.save(det);
                huboCambios = true;
                continue;
            }
            // Punto 2: TARDANZA cubierta por la papeleta recién aprobada (tipo "permiso"),
            // aún sin decisión RR.HH. registrada → se autoriza automáticamente. También repara
            // el caso histórico (Punto 1) de días YA autorizados manualmente ANTES de este fix,
            // que quedaron con PAPELETA_AUTORIZADA=1 pero MINUTOS_TARDANZA/OBSERVACION viejos
            // (ej. "289 min" / "NO AUTORIZADO" arrastrado) — sin esta segunda condición el
            // backfill los saltaba por considerarlos "ya resueltos". Idempotente: reaplicar sobre
            // un registro ya limpio no cambia nada.
            boolean yaAutorizadoPeroSucio = Integer.valueOf(1).equals(det.getPapeletaAutorizada())
                    && det.getMinutosTardanza() != null && det.getMinutosTardanza() > 0;
            if (papeletaAutorizacion != null && cubreFecha(papeletaAutorizacion, dia)
                    && ("TARDANZA".equals(tipoActual) || yaAutorizadoPeroSucio)) {
                autorizarPapeletaAutomaticamente(det, papeletaAutorizacion);
                detalleRepository.save(det);
                huboCambios = true;
            }
        }
        if (huboCambios) {
            recalcularCabeceraDesdeDetalle(cab);
        }
        return Optional.empty();
    }

    /**
     * Autoriza automáticamente una TARDANZA cubierta por una papeleta ya APROBADA por RR.HH.
     * (Punto 2 — decisión RR.HH.: no exigir que RR.HH. repita en "Consulta diaria" una decisión
     * que ya tomó al aprobar la papeleta). Aplica la misma limpieza que la autorización manual
     * (Punto 1): MINUTOS_TARDANZA y OBSERVACION quedan al día, no arrastran el valor de la
     * marcación física original.
     */
    private void autorizarPapeletaAutomaticamente(AsistenciaDetalle det, SolicitudRrhh papeleta) {
        det.setPapeletaAutorizada(1);
        det.setPapeletaDecisionUsuario("SISTEMA");
        det.setPapeletaDecisionFecha(LocalDateTime.now());
        det.setPapeletaMotivoRechazo(null);
        det.setTipoDia("LABORAL");
        det.setMinutosTardanza(0);
        det.setObservacion(observacionAutorizacionPapeleta(papeleta, true));
    }

    private String observacionAutorizacionPapeleta(SolicitudRrhh papeleta) {
        return observacionAutorizacionPapeleta(papeleta, false);
    }

    private String observacionAutorizacionPapeleta(SolicitudRrhh papeleta, boolean automatica) {
        StringBuilder sb = new StringBuilder(
                automatica ? "Papeleta autorizada automáticamente al aprobarse" : "Papeleta autorizada");
        TipoSolicitudRrhh tipo = papeleta.getTipoSolicitud();
        if (tipo != null && tipo.getNombre() != null) {
            sb.append(": ").append(tipo.getNombre());
        }
        if (papeleta.getId() != null) {
            sb.append(" N°").append(papeleta.getId());
        }
        if (papeleta.getFechaAprobacion() != null) {
            sb.append(" (aprobada ")
                    .append(papeleta.getFechaAprobacion().toLocalDate().format(FECHA_CORTA))
                    .append(")");
        }
        return sb.append('.').toString();
    }

    /**
     * Los periodos mensuales que el rango [ini, fin] toca, para ubicar cada cabecera.
     * Formato "yyyy-MM" (verificado contra INDECI_ASISTENCIA_CABECERA.PERIODO real, ej. "2026-07").
     */
    private List<String> periodosEntre(LocalDate ini, LocalDate fin) {
        List<String> periodos = new ArrayList<>();
        LocalDate cursor = ini.withDayOfMonth(1);
        LocalDate limite = fin.withDayOfMonth(1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        while (!cursor.isAfter(limite)) {
            periodos.add(cursor.format(fmt));
            cursor = cursor.plusMonths(1);
        }
        return periodos;
    }

    /**
     * Aplica el descuento de tardanza de dos niveles (V010_95) a la cabecera:
     * clasifica cada día por el umbral, acumula el Descuento 2 y persiste el
     * split (D1/D2). {@code DESCUENTO_TARDANZA} queda como el total (D1+D2), que
     * es lo que lee el motor de planilla. Fuente única para guardar y recalcular.
     */
    private void aplicarDescuentoTardanzaDosNiveles(
            AsistenciaCabecera cab, List<AsistenciaDiaDto> dias, double remun) {
        JornadaRegimen jornada = jornadaResolver.regimenDe(cab.getEmpleadoId());
        List<Integer> tardanzasDiarias = dias.stream()
                .filter(d -> "TARDANZA".equals(d.getTipoDia()) && d.getMinutosTardanza() != null)
                .map(AsistenciaDiaDto::getMinutosTardanza)
                .toList();
        int umbral = (jornada != null && jornada.getUmbralTardanzaDiariaMin() != null)
                ? jornada.getUmbralTardanzaDiariaMin() : 10;
        int tope = (jornada != null && jornada.getTopeTardanzaMensualMin() != null)
                ? jornada.getTopeTardanzaMensualMin() : 60;
        BigDecimal jornadaHoras = (jornada != null && jornada.getJornadaHoras() != null)
                ? jornada.getJornadaHoras() : BigDecimal.valueOf(8);

        TardanzaDescuentoCalculator.Resultado split =
                TardanzaDescuentoCalculator.calcular(tardanzasDiarias, remun, jornadaHoras, umbral, tope);
        cab.setMinTardanzaDiaria(split.getMinTardanzaDiaria());
        cab.setMinTardanzaMenorAcum(split.getMinTardanzaMenorAcum());
        cab.setMinTardanzaExcesoMes(split.getMinTardanzaExcesoMes());
        cab.setTotalMinTardanza(split.getMinTardanzaDiaria() + split.getMinTardanzaMenorAcum());
        cab.setDescuentoTardanzaDiaria(split.getDescuentoDiaria());
        cab.setDescuentoTardanzaMensual(split.getDescuentoMensual());
        cab.setDescuentoTardanza(split.getDescuentoTotal());
    }

    /**
     * Aplica el descuento de salida anticipada de dos niveles (V012_56) a la cabecera —
     * espejo de {@link #aplicarDescuentoTardanzaDosNiveles}, con parámetros propios
     * (decisión RR.HH. 2026-08-07: no comparte umbral/tope con tardanza). Solo cuenta los días
     * que SIGUEN en {@code OBSERVADO}: si una papeleta ya justificó la salida anticipada
     * (ver {@link com.indeci.rrhh.service.asistencia.PapeletaJustificacionResolver#justificarSalidaAnticipada}),
     * el día pasa a LABORAL y queda fuera de este cálculo. {@code DESCUENTO_SALIDA_ANTICIPADA}
     * queda como el total (D1+D2), que es lo que lee el motor de planilla.
     */
    private void aplicarDescuentoSalidaAnticipadaDosNiveles(
            AsistenciaCabecera cab, List<AsistenciaDiaDto> dias, double remun) {
        JornadaRegimen jornada = jornadaResolver.regimenDe(cab.getEmpleadoId());
        List<Integer> salidasAnticipadasDiarias = dias.stream()
                .filter(d -> "OBSERVADO".equals(d.getTipoDia()) && d.getMinutosSalidaAnticipada() != null)
                .map(AsistenciaDiaDto::getMinutosSalidaAnticipada)
                .toList();
        int umbral = (jornada != null && jornada.getUmbralSalidaAnticDiariaMin() != null)
                ? jornada.getUmbralSalidaAnticDiariaMin() : 10;
        int tope = (jornada != null && jornada.getTopeSalidaAnticMensualMin() != null)
                ? jornada.getTopeSalidaAnticMensualMin() : 60;
        BigDecimal jornadaHoras = (jornada != null && jornada.getJornadaHoras() != null)
                ? jornada.getJornadaHoras() : BigDecimal.valueOf(8);

        SalidaAnticipadaDescuentoCalculator.Resultado split = SalidaAnticipadaDescuentoCalculator
                .calcular(salidasAnticipadasDiarias, remun, jornadaHoras, umbral, tope);
        cab.setMinSalidaAnticDiaria(split.getMinSalidaAnticDiaria());
        cab.setMinSalidaAnticMenorAcum(split.getMinSalidaAnticMenorAcum());
        cab.setMinSalidaAnticExcesoMes(split.getMinSalidaAnticExcesoMes());
        cab.setDescuentoSalidaAnticDiaria(split.getDescuentoDiaria());
        cab.setDescuentoSalidaAnticMensual(split.getDescuentoMensual());
        cab.setDescuentoSalidaAnticipada(split.getDescuentoTotal());
    }

    private AsistenciaDiaDto aDiaDto(AsistenciaDetalle det) {
        AsistenciaDiaDto d = new AsistenciaDiaDto();
        d.setDia(det.getDia());
        d.setTipoDia(det.getTipoDia());
        d.setMinutosTardanza(det.getMinutosTardanza());
        d.setObservacion(det.getObservacion());
        d.setDiaSemana(det.getDiaSemana());
        d.setMarcaEntrada(det.getMarcaEntrada());
        d.setMarcaSalida(det.getMarcaSalida());
        d.setMarca3(det.getMarca3());
        d.setMarca4(det.getMarca4());
        d.setHoraEntradaEsperada(det.getHoraEntradaEsperada());
        d.setMinutosSalidaAnticipada(det.getMinutosSalidaAnticipada());
        d.setHorasTrabajadasMin(det.getHorasTrabajadasMin());
        d.setHorasExtra25Min(det.getHorasExtra25Min());
        d.setHorasExtra35Min(det.getHorasExtra35Min());
        d.setHorasExtra100Min(det.getHorasExtra100Min());
        d.setHorasExtraTotalMin(det.getHorasExtraTotalMin());
        d.setOrigen(det.getOrigen());
        d.setPapeletaAutorizada(det.getPapeletaAutorizada());
        return d;
    }

    private String limpiarFiltro(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private AsistenciaDiariaRowDto mapearDiariaRow(Object[] row) {
        AsistenciaDiariaRowDto dto = new AsistenciaDiariaRowDto();
        dto.setDetalleId((Long) row[0]);
        dto.setCabeceraId((Long) row[1]);
        dto.setEmpleadoId((Long) row[2]);
        dto.setDni((String) row[3]);
        dto.setNombreCompleto((String) row[4]);
        dto.setFecha((LocalDate) row[5]);
        dto.setMarcaEntrada((String) row[6]);
        dto.setMarcaSalida((String) row[7]);
        dto.setTipoDia((String) row[8]);
        dto.setHorasTrabajadasMin((Integer) row[9]);
        dto.setMinutosSalidaAnticipada((Integer) row[10]);
        dto.setPeriodo((String) row[11]);
        dto.setOrigen((String) row[12]);
        dto.setMinutosTardanza((Integer) row[13]);
        dto.setObservacion((String) row[14]);
        dto.setMarca3((String) row[15]);
        dto.setMarca4((String) row[16]);
        dto.setHoraEntradaEsperada((String) row[17]);
        dto.setHorasExtra25Min((Integer) row[18]);
        dto.setHorasExtra35Min((Integer) row[19]);
        dto.setHorasExtra100Min((Integer) row[20]);
        dto.setHorasExtraTotalMin((Integer) row[21]);
        dto.setPapeletaAutorizada((Integer) row[22]);
        dto.setPapeletaMotivoRechazo((String) row[23]);
        dto.setImportacionId((Long) row[24]);
        return dto;
    }

    private AsistenciaDiariaRowDto mapearDiariaRow(AsistenciaDetalle det, AsistenciaCabecera cab) {
        return construirDiariaRowDto(det, cab);
    }

    private AsistenciaDiariaRowDto construirDiariaRowDto(
            AsistenciaDetalle det,
            AsistenciaCabecera cab) {
        AsistenciaDiariaRowDto dto = new AsistenciaDiariaRowDto();
        dto.setDetalleId(det.getId());
        dto.setCabeceraId(cab.getId());
        dto.setImportacionId(cab.getImportacionId());
        dto.setEmpleadoId(cab.getEmpleadoId());
        dto.setFecha(det.getDia());
        dto.setMarcaEntrada(det.getMarcaEntrada());
        dto.setMarcaSalida(det.getMarcaSalida());
        dto.setTipoDia(det.getTipoDia());
        dto.setHorasTrabajadasMin(det.getHorasTrabajadasMin());
        dto.setMinutosSalidaAnticipada(det.getMinutosSalidaAnticipada());
        dto.setPeriodo(cab.getPeriodo());
        dto.setOrigen(det.getOrigen());
        dto.setMinutosTardanza(det.getMinutosTardanza());
        dto.setObservacion(det.getObservacion());
        dto.setMarca3(det.getMarca3());
        dto.setMarca4(det.getMarca4());
        dto.setHoraEntradaEsperada(det.getHoraEntradaEsperada());
        dto.setHorasExtra25Min(det.getHorasExtra25Min());
        dto.setHorasExtra35Min(det.getHorasExtra35Min());
        dto.setHorasExtra100Min(det.getHorasExtra100Min());
        dto.setHorasExtraTotalMin(det.getHorasExtraTotalMin());
        dto.setPapeletaAutorizada(det.getPapeletaAutorizada());
        dto.setPapeletaMotivoRechazo(det.getPapeletaMotivoRechazo());
        dto.setPapeletaDecisionUsuario(det.getPapeletaDecisionUsuario());
        dto.setPapeletaDecisionFecha(det.getPapeletaDecisionFecha());
        empleadoRepository.findPersonaResumenByEmpleadoIds(List.of(cab.getEmpleadoId()))
                .stream()
                .findFirst()
                .ifPresent(row -> {
                    dto.setNombreCompleto((String) row[1]);
                    dto.setDni((String) row[2]);
                });
        return dto;
    }

    // ==========================================
    // GUARDAR (UPSERT por empleado + período)
    // ==========================================

    @Auditable(accion = "GUARDAR_ASISTENCIA")
    @Transactional
    public void guardar(AsistenciaGuardarDto dto) {

        if (dto.getEmpleadoId() == null
                || dto.getPeriodo() == null
                || dto.getPeriodo().isBlank()) {
            throw new NegocioException(
                    "Empleado y período son obligatorios");
        }

        List<AsistenciaDiaDto> dias =
                dto.getDias() != null ? dto.getDias() : new ArrayList<>();

        validarTiposDia(dias);

        double remun = dto.getRemuneracionBase() != null
                ? dto.getRemuneracionBase()
                : 0.0;

        AsistenciaResumenCalculator.Resumen agregados =
                AsistenciaResumenCalculator.calcular(dias, remun);

        // ----- UPSERT de la cabecera -----
        AsistenciaCabecera cab = cabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(
                        dto.getEmpleadoId(), dto.getPeriodo(), 1)
                .orElseGet(() -> {
                    AsistenciaCabecera nueva = new AsistenciaCabecera();
                    nueva.setEmpleadoId(dto.getEmpleadoId());
                    nueva.setPeriodo(dto.getPeriodo());
                    nueva.setActivo(1);
                    nueva.setCreatedAt(LocalDateTime.now());
                    return nueva;
                });

        cab.setRemuneracionBase(dto.getRemuneracionBase());
        cab.setDiasLaborados(agregados.getDiasLaborados());
        cab.setDiasFalta(agregados.getDiasFalta());
        cab.setDescuentoFalta(agregados.getDescuentoFalta());
        cab.setMinutosSalidaAnticipada(agregados.getMinutosSalidaAnticipada());
        cab.setMarcasIncompletas(agregados.getMarcasIncompletas());
        cab.setObservacion(dto.getObservacion());
        cab.setEstado(normalizarEstado(dto.getEstado()));

        // Fix 3 — tardanza con el modelo de dos niveles (no pisar D1/D2 con single-tier).
        aplicarDescuentoTardanzaDosNiveles(cab, dias, remun);
        aplicarDescuentoSalidaAnticipadaDosNiveles(cab, dias, remun);

        AsistenciaCabecera guardada = cabeceraRepository.save(cab);

        // Fix 2 — preservar las marcas del reloj: la edición manual envía los días
        // sin marcas (el GET no las devuelve), así que se conservan desde el detalle
        // existente por día antes de reemplazarlo.
        Map<LocalDate, AsistenciaDetalle> previoPorDia =
                detalleRepository.findByCabeceraIdOrderByDia(guardada.getId()).stream()
                        .collect(Collectors.toMap(
                                AsistenciaDetalle::getDia, d -> d, (a, b) -> a));

        // ----- Reemplazar el detalle -----
        detalleRepository.deleteByCabeceraId(guardada.getId());
        // Fix 1 — forzar el DELETE antes de los INSERT: sin esto Hibernate inserta
        // primero y choca con INDECI_ASIST_DET_UK (CABECERA_ID, DIA) → ORA-00001.
        detalleRepository.flush();

        detalleJdbcWriter.insertarLote(mapearDetalles(guardada.getId(), dias, previoPorDia));

        auditoriaContext.setDetalle(
                "Asistencia guardada empleado " + dto.getEmpleadoId()
                        + " período " + dto.getPeriodo()
                        + " (" + dias.size() + " días)");
    }

    /**
     * Persiste asistencia proveniente de importación masiva del marcador, con
     * VERSIONADO (F5 / P4): si ya existe una cabecera activa para el empleado+periodo,
     * se conserva como ACTIVO=0 (con su detalle histórico intacto) y se crea una
     * nueva cabecera ACTIVO=1 con VERSION = maxVersion+1 y la auditoría de
     * rectificación. El motor M05 sigue leyendo siempre la cabecera ACTIVO=1.
     *
     * @param motivoRectificacion   motivo cuando la operación reemplaza una versión previa (puede ser null en la primera carga)
     * @param usuarioRectificacion  usuario que ejecuta la rectificación
     * @param autorizadoPor         usuario con rol autorizado que respalda la rectificación
     */
    @Transactional
    public void guardarImportacion(
            Long empleadoId,
            String periodo,
            double remuneracionBase,
            String baseOrigen,
            String estado,
            Long importacionId,
            List<AsistenciaDiaDto> dias,
            String motivoRectificacion,
            String usuarioRectificacion,
            String autorizadoPor) {

        validarTiposDia(dias);

        AsistenciaCabecera anterior = cabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .orElse(null);
        boolean esRectificacion = anterior != null;

        // Fix integridad — fusiona los días del archivo nuevo con los días de la versión
        // activa anterior que caen FUERA del rango de esta carga, para que la cabecera
        // nueva (única fuente que lee el motor M05) no pierda cobertura ya cargada.
        List<AsistenciaDiaDto> diasFinal = fusionarConAnterior(dias, anterior);
        AsistenciaResumenCalculator.Resumen agregados =
                AsistenciaResumenCalculator.calcular(diasFinal, remuneracionBase);

        if (esRectificacion) {
            // Conserva la versión anterior como histórico (NO se borra su detalle).
            // saveAndFlush garantiza que el UPDATE a ACTIVO=0 ocurra ANTES del INSERT
            // de la nueva activa, respetando el índice único single-active.
            anterior.setActivo(0);
            cabeceraRepository.saveAndFlush(anterior);
        }

        Integer maxVersion = cabeceraRepository.maxVersion(empleadoId, periodo);
        int nuevaVersion = (maxVersion != null ? maxVersion : 0) + 1;

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEmpleadoId(empleadoId);
        cab.setPeriodo(periodo);
        cab.setActivo(1);
        cab.setVersion(nuevaVersion);
        cab.setCreatedAt(LocalDateTime.now());
        cab.setRemuneracionBase(remuneracionBase);
        cab.setDiasLaborados(agregados.getDiasLaborados());
        cab.setDiasFalta(agregados.getDiasFalta());
        cab.setTotalMinTardanza(agregados.getTotalMinTardanza());
        cab.setDescuentoTardanza(agregados.getDescuentoTardanza());
        cab.setDescuentoFalta(agregados.getDescuentoFalta());
        cab.setMinutosSalidaAnticipada(agregados.getMinutosSalidaAnticipada());
        cab.setMarcasIncompletas(agregados.getMarcasIncompletas());
        cab.setBaseAsistenciaOrigen(baseOrigen);
        cab.setImportacionId(importacionId);
        cab.setEstado(normalizarEstado(estado));
        cab.setObservacion("Importación marcador ID " + importacionId
                + (esRectificacion ? " (rectificación v" + nuevaVersion + ")" : ""));
        if (esRectificacion) {
            cab.setMotivoRectificacion(motivoRectificacion);
            cab.setUsuarioRectificacion(usuarioRectificacion);
            cab.setFechaRectificacion(LocalDateTime.now());
            cab.setAutorizadoPor(autorizadoPor);
        }

        AsistenciaCabecera guardada = cabeceraRepository.save(cab);
        // Detalle nuevo sobre la cabecera nueva: no se borra detalle de versiones previas.
        detalleJdbcWriter.insertarLote(mapearDetalles(guardada.getId(), diasFinal));
    }

    /**
     * Fix integridad de asistencia — une los días del archivo recién importado con los
     * días de la versión ACTIVA anterior que caen FUERA del rango del archivo nuevo, para
     * que la cabecera nueva (única fuente que lee el motor M05) siga cubriendo todo lo
     * que ya se había cargado. Gana el archivo nuevo cuando ambos traen el mismo día.
     */
    private List<AsistenciaDiaDto> fusionarConAnterior(
            List<AsistenciaDiaDto> dias, AsistenciaCabecera anterior) {
        if (anterior == null) {
            return dias; // primera carga del período: nada que fusionar
        }
        Set<LocalDate> diasNuevos = dias.stream()
                .map(AsistenciaDiaDto::getDia)
                .collect(Collectors.toSet());
        List<AsistenciaDiaDto> diasFinal = new ArrayList<>(dias);
        detalleRepository.findByCabeceraIdOrderByDia(anterior.getId()).stream()
                .filter(det -> det.getDia() != null && !diasNuevos.contains(det.getDia()))
                .map(this::aDiaDto)
                .forEach(diasFinal::add);
        diasFinal.sort(Comparator.comparing(AsistenciaDiaDto::getDia));
        return diasFinal;
    }

    void validarTiposDia(List<AsistenciaDiaDto> dias) {
        for (AsistenciaDiaDto d : dias) {
            String tipo = d.getTipoDia();
            if (tipo == null || !TIPOS_DIA.contains(tipo)) {
                throw new NegocioException("Tipo de día inválido: " + tipo);
            }
            if (TIPO_DIA_SANCION_PAD.equals(tipo)
                    && (d.getObservacion() == null || d.getObservacion().isBlank())) {
                throw new NegocioException(
                        "Debe indicar el motivo/expediente PAD para el día "
                                + d.getDia() + " marcado como Sanción PAD.");
            }
        }
    }

    String normalizarEstado(String estado) {
        if (estado != null && ESTADOS_CABECERA.contains(estado)) {
            return estado;
        }
        return "BORRADOR";
    }

    /**
     * ROUND((remuneracion / 30 / 8 / 60) * minutos, 2) — expuesto para tests.
     */
    double calcularDescuentoTardanza(double remuneracion, int minutos) {
        return AsistenciaResumenCalculator.calcularDescuentoTardanza(remuneracion, minutos);
    }

    /** ROUND((remuneracion / 30) * dias_falta, 2) — expuesto para tests. */
    double calcularDescuentoFalta(double remuneracion, int diasFalta) {
        return AsistenciaResumenCalculator.calcularDescuentoFalta(remuneracion, diasFalta);
    }

    private List<AsistenciaDetalle> mapearDetalles(Long cabeceraId, List<AsistenciaDiaDto> dias) {
        return mapearDetalles(cabeceraId, dias, java.util.Map.of());
    }

    /** Recorta un texto a {@code max} caracteres (null-safe) para respetar el ancho de la columna. */
    private static String truncar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    /**
     * Mapea los días a detalle. Los campos del DTO mandan; los datos del reloj
     * (marcas, hora esperada, horas trabajadas/extra) que NO llegan en el DTO se
     * conservan desde el detalle previo del mismo día ({@code previoPorDia}) — así
     * la edición manual no borra las marcas importadas (de las que depende Recalcular).
     */
    private List<AsistenciaDetalle> mapearDetalles(
            Long cabeceraId,
            List<AsistenciaDiaDto> dias,
            Map<LocalDate, AsistenciaDetalle> previoPorDia) {
        List<AsistenciaDetalle> detalles = new ArrayList<>();
        for (AsistenciaDiaDto d : dias) {
            AsistenciaDetalle prev = previoPorDia.get(d.getDia());
            AsistenciaDetalle det = new AsistenciaDetalle();
            det.setCabeceraId(cabeceraId);
            det.setDia(d.getDia());
            det.setTipoDia(d.getTipoDia());
            det.setMinutosTardanza(
                    d.getMinutosTardanza() != null ? Math.max(0, d.getMinutosTardanza()) : 0);
            // OBSERVACION es VARCHAR2(200): se trunca para no reventar con ORA-12899 ante
            // mensajes largos (motivos de validación, justificaciones, etc.).
            det.setObservacion(truncar(d.getObservacion(), 200));
            det.setMarcaEntrada(coalesce(d.getMarcaEntrada(), prev != null ? prev.getMarcaEntrada() : null));
            det.setMarcaSalida(coalesce(d.getMarcaSalida(), prev != null ? prev.getMarcaSalida() : null));
            det.setMarca3(coalesce(d.getMarca3(), prev != null ? prev.getMarca3() : null));
            det.setMarca4(coalesce(d.getMarca4(), prev != null ? prev.getMarca4() : null));
            det.setHoraEntradaEsperada(
                    coalesce(d.getHoraEntradaEsperada(), prev != null ? prev.getHoraEntradaEsperada() : null));
            det.setMinutosSalidaAnticipada(coalesceInt(d.getMinutosSalidaAnticipada(),
                    prev != null ? prev.getMinutosSalidaAnticipada() : null));
            det.setHorasTrabajadasMin(coalesceInt(d.getHorasTrabajadasMin(),
                    prev != null ? prev.getHorasTrabajadasMin() : null));
            det.setHorasExtra25Min(coalesceInt(d.getHorasExtra25Min(),
                    prev != null ? prev.getHorasExtra25Min() : null));
            det.setHorasExtra35Min(coalesceInt(d.getHorasExtra35Min(),
                    prev != null ? prev.getHorasExtra35Min() : null));
            det.setHorasExtra100Min(coalesceInt(d.getHorasExtra100Min(),
                    prev != null ? prev.getHorasExtra100Min() : null));
            det.setHorasExtraTotalMin(coalesceInt(d.getHorasExtraTotalMin(),
                    prev != null ? prev.getHorasExtraTotalMin() : null));
            det.setDiaSemana(coalesce(d.getDiaSemana(), prev != null ? prev.getDiaSemana() : null));
            det.setOrigen(d.getOrigen() != null ? d.getOrigen()
                    : (prev != null && prev.getOrigen() != null ? prev.getOrigen() : "MANUAL"));
            detalles.add(det);
        }
        return detalles;
    }

    private static String coalesce(String a, String b) {
        return a != null ? a : b;
    }

    /** Valor del DTO si llega; si no, el del detalle previo; si tampoco, 0. */
    private static int coalesceInt(Integer dto, Integer prev) {
        if (dto != null) return dto;
        return prev != null ? prev : 0;
    }

    // ==========================================
    // MAPEO
    // ==========================================

    private AsistenciaResponseDto aResponse(AsistenciaCabecera cab) {

        AsistenciaResponseDto dto = new AsistenciaResponseDto();

        dto.setId(cab.getId());
        dto.setEmpleadoId(cab.getEmpleadoId());
        dto.setPeriodo(cab.getPeriodo());
        dto.setRemuneracionBase(cab.getRemuneracionBase());
        dto.setDiasLaborados(cab.getDiasLaborados());
        dto.setDiasFalta(cab.getDiasFalta());
        dto.setTotalMinTardanza(cab.getTotalMinTardanza());
        dto.setDescuentoTardanza(cab.getDescuentoTardanza());
        dto.setDescuentoFalta(cab.getDescuentoFalta());
        // V010_95 — agregados del modelo de dos niveles + umbral vigente del régimen.
        dto.setMinTardanzaDiaria(cab.getMinTardanzaDiaria());
        dto.setMinTardanzaMenorAcum(cab.getMinTardanzaMenorAcum());
        dto.setMinTardanzaExcesoMes(cab.getMinTardanzaExcesoMes());
        dto.setDescuentoTardanzaDiaria(cab.getDescuentoTardanzaDiaria());
        dto.setDescuentoTardanzaMensual(cab.getDescuentoTardanzaMensual());
        JornadaRegimen jornada = jornadaResolver.regimenDe(cab.getEmpleadoId());
        dto.setUmbralTardanzaDiariaMin(
                jornada != null && jornada.getUmbralTardanzaDiariaMin() != null
                        ? jornada.getUmbralTardanzaDiariaMin() : 10);
        dto.setEstado(cab.getEstado());
        dto.setObservacion(cab.getObservacion());

        List<AsistenciaDiaDto> dias =
                detalleRepository.findByCabeceraIdOrderByDia(cab.getId())
                        .stream()
                        .map(this::aDiaDto)
                        .toList();
        dto.setDias(new ArrayList<>(dias));

        return dto;
    }
}
