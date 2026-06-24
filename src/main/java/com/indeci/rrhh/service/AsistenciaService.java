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
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaResumenCalculator;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
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
import java.util.ArrayList;
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
    private final EmpleadoRepository empleadoRepository;
    private final PeriodoPlanillaRepository periodoPlanillaRepository;
    private final AuditoriaContext auditoriaContext;
    private final BaseAsistenciaResolver baseResolver;
    private final SolicitudRrhhRepository solicitudRrhhRepository;
    private final TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;
    private final JornadaRegimenRepository jornadaRegimenRepository;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;

    /** ESTADO_SOLICITUD_ID = 9 → APROBADA. */
    private static final long ESTADO_SOLICITUD_APROBADA = 9L;

    /** Códigos de tipo de solicitud que cuentan como permiso/papeleta en asistencia. */
    private static final Set<String> CODIGOS_PERMISO_ASISTENCIA = Set.of(
            "001", "002", "003", "004", "005", "006", "008", "009", "010", "011", "012");

    /** Tipos de día válidos (espejo del CHECK INDECI_ASIST_DET_TIPO_CK). */
    private static final Set<String> TIPOS_DIA = Set.of(
            "LABORAL", "FALTA", "TARDANZA", "LICENCIA", "VACACIONES", "DESCANSO",
            "FERIADO", "OBSERVADO");

    private static final Set<String> ESTADOS_CABECERA = Set.of(
            "BORRADOR", "PREVALIDADA", "LISTA_PARA_VALIDAR", "OBSERVADA", "VALIDADA");

    /** Tipos que cuentan como día efectivamente laborado. */
    private static final Set<String> TIPOS_LABORADOS = Set.of("LABORAL", "TARDANZA");

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

    @Transactional(readOnly = true)
    public Page<AsistenciaDiariaRowDto> listarDiaria(
            LocalDate fecha,
            String dni,
            String q,
            Pageable pageable) {
        if (fecha == null) {
            throw new NegocioException("La fecha es obligatoria.");
        }
        String dniFiltro = limpiarFiltro(dni);
        String qFiltro = limpiarFiltro(q);
        Page<AsistenciaDiariaRowDto> page = detalleRepository
                .buscarDiaria(fecha, dniFiltro, qFiltro, pageable)
                .map(this::mapearDiariaRow);
        enriquecerPapeletas(page.getContent(), fecha);
        return page;
    }

    /**
     * Cruza cada fila con INDECI_SOLICITUD_RRHH para marcar si el empleado tiene una
     * papeleta/permiso APROBADA (ESTADO_SOLICITUD_ID = 9) de un tipo de asistencia que
     * cubre la fecha del día. Expone tipo, motivo y el tiempo del permiso.
     */
    private void enriquecerPapeletas(List<AsistenciaDiariaRowDto> rows, LocalDate fecha) {
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

        Map<Long, SolicitudRrhh> papeletaPorEmpleado = new HashMap<>();
        for (SolicitudRrhh s : solicitudRrhhRepository.findByEmpleadoIdInAndActivo(empleadoIds, 1)) {
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
            papeletaPorEmpleado.putIfAbsent(s.getEmpleadoId(), s);
        }

        for (AsistenciaDiariaRowDto row : rows) {
            SolicitudRrhh s = papeletaPorEmpleado.get(row.getEmpleadoId());
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

        boolean tienePapeleta = buscarPapeletaAprobada(cab.getEmpleadoId(), det.getDia()).isPresent();
        if (tienePapeleta) {
            aplicarDecisionPapeleta(det, dto);
        } else if (dto.getTipoDia() != null) {
            if (!TIPOS_DIA.contains(dto.getTipoDia())) {
                throw new NegocioException("Tipo de día inválido: " + dto.getTipoDia());
            }
            det.setTipoDia(dto.getTipoDia());
        }
        if (dto.getMarcaEntrada() != null) {
            det.setMarcaEntrada(dto.getMarcaEntrada().isBlank() ? null : dto.getMarcaEntrada().trim());
        }
        if (dto.getMarcaSalida() != null) {
            det.setMarcaSalida(dto.getMarcaSalida().isBlank() ? null : dto.getMarcaSalida().trim());
        }
        if (dto.getMinutosTardanza() != null) {
            det.setMinutosTardanza(Math.max(0, dto.getMinutosTardanza()));
        }
        if (dto.getObservacion() != null) {
            det.setObservacion(dto.getObservacion().isBlank() ? null : dto.getObservacion().trim());
        }
        det.setOrigen("MANUAL");
        detalleRepository.save(det);

        recalcularCabeceraDesdeDetalle(cab);

        auditoriaContext.setDetalle(
                "Asistencia diaria editada detalle " + detalleId
                        + " empleado " + cab.getEmpleadoId()
                        + " fecha " + det.getDia());

        AsistenciaDiariaRowDto row = construirDiariaRowDto(det, cab);
        enriquecerPapeletas(List.of(row), det.getDia());
        return row;
    }

    private void aplicarDecisionPapeleta(AsistenciaDetalle det, AsistenciaDiariaEditDto dto) {
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
        cabeceraRepository.save(cab);
    }

    /**
     * Aplica el descuento de tardanza de dos niveles (V010_95) a la cabecera:
     * clasifica cada día por el umbral, acumula el Descuento 2 y persiste el
     * split (D1/D2). {@code DESCUENTO_TARDANZA} queda como el total (D1+D2), que
     * es lo que lee el motor de planilla. Fuente única para guardar y recalcular.
     */
    private void aplicarDescuentoTardanzaDosNiveles(
            AsistenciaCabecera cab, List<AsistenciaDiaDto> dias, double remun) {
        JornadaRegimen jornada = jornadaDeEmpleado(cab.getEmpleadoId());
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

    /** Jornada vigente del empleado vía su régimen en INDECI_EMPLEADO_PLANILLA. */
    private JornadaRegimen jornadaDeEmpleado(Long empleadoId) {
        if (empleadoId == null) {
            return null;
        }
        Long regimenId = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(EmpleadoPlanilla::getRegimenLaboralId)
                .orElse(null);
        if (regimenId == null) {
            return null;
        }
        return jornadaRegimenRepository.findByRegimenLaboralId(regimenId).orElse(null);
    }

    private AsistenciaDiaDto aDiaDto(AsistenciaDetalle det) {
        AsistenciaDiaDto d = new AsistenciaDiaDto();
        d.setDia(det.getDia());
        d.setTipoDia(det.getTipoDia());
        d.setMinutosTardanza(det.getMinutosTardanza());
        d.setObservacion(det.getObservacion());
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

        detalleRepository.saveAll(mapearDetalles(guardada.getId(), dias, previoPorDia));

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
        AsistenciaResumenCalculator.Resumen agregados =
                AsistenciaResumenCalculator.calcular(dias, remuneracionBase);

        AsistenciaCabecera anterior = cabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .orElse(null);
        boolean esRectificacion = anterior != null;
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
        detalleRepository.saveAll(mapearDetalles(guardada.getId(), dias));
    }

    void validarTiposDia(List<AsistenciaDiaDto> dias) {
        for (AsistenciaDiaDto d : dias) {
            String tipo = d.getTipoDia();
            if (tipo == null || !TIPOS_DIA.contains(tipo)) {
                throw new NegocioException("Tipo de día inválido: " + tipo);
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
            det.setObservacion(d.getObservacion());
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
        JornadaRegimen jornada = jornadaDeEmpleado(cab.getEmpleadoId());
        dto.setUmbralTardanzaDiariaMin(
                jornada != null && jornada.getUmbralTardanzaDiariaMin() != null
                        ? jornada.getUmbralTardanzaDiariaMin() : 10);
        dto.setEstado(cab.getEstado());
        dto.setObservacion(cab.getObservacion());

        List<AsistenciaDiaDto> dias =
                detalleRepository.findByCabeceraIdOrderByDia(cab.getId())
                        .stream()
                        .map(det -> {
                            AsistenciaDiaDto d = new AsistenciaDiaDto();
                            d.setDia(det.getDia());
                            d.setTipoDia(det.getTipoDia());
                            d.setMinutosTardanza(det.getMinutosTardanza());
                            d.setObservacion(det.getObservacion());
                            return d;
                        })
                        .toList();
        dto.setDias(new ArrayList<>(dias));

        return dto;
    }
}
