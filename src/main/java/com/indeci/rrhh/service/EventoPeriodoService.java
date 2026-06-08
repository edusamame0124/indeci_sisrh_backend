package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EventoDistribucionMesDto;
import com.indeci.rrhh.dto.EventoPeriodoDto;
import com.indeci.rrhh.dto.EventoPeriodoPageDto;
import com.indeci.rrhh.dto.EventoPeriodoResponseDto;
import com.indeci.rrhh.dto.MaternidadPreviewDto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.EventoDistribucionMes;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EventoDistribucionMesRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;
import com.indeci.rrhh.service.support.DistribucionMensualCalculator;
import com.indeci.rrhh.validation.MaternidadEventoValidator;

import lombok.RequiredArgsConstructor;

/**
 * F2.5 — CRUD de {@code INDECI_EMPLEADO_EVENTO} con validaciones.
 * P0 maternidad: campos normativos + desglose mensual persistido.
 */
@Service
@RequiredArgsConstructor
public class EventoPeriodoService {

    private static final Set<String> ESTADOS_VALIDOS =
            Set.of("REGISTRADO", "VALIDADO", "RECHAZADO");
    private static final String CODIGO_MATERNIDAD = "MATERNIDAD";
    private static final String PLAME_MATERNIDAD = "0915";

    private final EmpleadoEventoRepository repository;
    private final EmpleadoRepository empleadoRepository;
    private final TipoEventoRepository tipoRepository;
    private final EventoDistribucionMesRepository distribucionRepository;
    private final AuditoriaContext auditoriaContext;

    @Transactional(readOnly = true)
    public EventoPeriodoPageDto listarPaginado(
            Long empleadoId,
            Long tipoEventoId,
            String estado,
            int page,
            int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Page<EmpleadoEvento> result = repository.findBandejaPaginada(
                empleadoId,
                tipoEventoId,
                estado,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "fechaInicio", "id")));
        Map<Long, String[]> personas = construirCachePersonas(result.getContent());
        List<EventoPeriodoResponseDto> content = result.getContent().stream()
                .map(e -> toResponse(e, personas))
                .toList();
        return new EventoPeriodoPageDto(
                content,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize());
    }

    @Transactional(readOnly = true)
    public List<EventoPeriodoResponseDto> listarPorEmpleado(Long empleadoId) {
        if (empleadoId == null) {
            throw new NegocioException("empleadoId requerido");
        }
        Map<Long, String[]> personas = Map.of(
                empleadoId, resolverPersonaEmpleado(empleadoId));
        return repository
                .findByEmpleadoIdAndActivoOrderByFechaInicioDesc(empleadoId, 1)
                .stream()
                .map(e -> toResponse(e, personas))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventoPeriodoResponseDto obtener(Long id) {
        return toResponse(buscar(id));
    }

    @Transactional
    @Auditable(accion = "CREAR_EVENTO_PERIODO")
    public EventoPeriodoResponseDto crear(EventoPeriodoDto dto) {
        TipoEvento tipo = validar(dto, null);

        EmpleadoEvento entity = mapearEntidad(new EmpleadoEvento(), dto, tipo);
        entity.setEstado("REGISTRADO");
        entity.setActivo(1);
        entity.setCreatedAt(LocalDateTime.now());

        EmpleadoEvento guardada = repository.save(entity);
        persistirDistribucion(guardada.getId(), dto, esMaternidad(tipo));

        auditoriaContext.setDetalle(detalleAuditoria(tipo, dto));
        return toResponse(guardada);
    }

    @Transactional
    @Auditable(accion = "ACTUALIZAR_EVENTO_PERIODO")
    public EventoPeriodoResponseDto actualizar(Long id, EventoPeriodoDto dto) {
        EmpleadoEvento entity = buscar(id);
        TipoEvento tipo = validar(dto, id);

        mapearEntidad(entity, dto, tipo);
        EmpleadoEvento guardada = repository.save(entity);

        distribucionRepository.deleteByEmpleadoEventoId(id);
        persistirDistribucion(id, dto, esMaternidad(tipo));

        auditoriaContext.setDetalle("Evento actualizado ID: " + id);
        return toResponse(guardada);
    }

    public MaternidadPreviewDto previewMaternidad(EventoPeriodoDto dto) {
        if (dto.getFechaInicio() == null || dto.getDuracionLegal() == null) {
            throw new NegocioException(
                    "Indique fecha de inicio y duración legal para previsualizar.");
        }
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(
                dto.getFechaInicio(), dto.getDuracionLegal());
        List<EventoDistribucionMesDto> tramos =
                DistribucionMensualCalculator.calcular(dto.getFechaInicio(), fin);

        MaternidadPreviewDto preview = new MaternidadPreviewDto();
        preview.setDistribucionMensual(tramos);
        preview.setCantidadPeriodos(tramos.size());
        preview.setCruzaMeses(tramos.size() > 1);
        preview.setCodigoPlameSunat(PLAME_MATERNIDAD);
        preview.setAfectaDiasLaborados(true);
        preview.setGeneraSubsidio(true);
        preview.setSumaAlNeto(tramos.size() == 1);
        preview.setMensajeGuardrail(tramos.size() > 1
                ? "Este descanso cruza varios meses. El sistema lo distribuirá "
                        + "automáticamente por periodo. La imputación del subsidio al neto "
                        + "se aplicará periodo a periodo en una versión posterior."
                : "El descanso queda dentro de un solo periodo de planilla.");
        return preview;
    }

    @Auditable(accion = "CAMBIAR_ESTADO_EVENTO")
    public EventoPeriodoResponseDto cambiarEstado(Long id, String nuevoEstado) {
        if (nuevoEstado == null || !ESTADOS_VALIDOS.contains(nuevoEstado.toUpperCase())) {
            throw new NegocioException(
                    "Estado inválido. Permitidos: " + ESTADOS_VALIDOS);
        }
        EmpleadoEvento entity = buscar(id);
        entity.setEstado(nuevoEstado.toUpperCase());
        EmpleadoEvento guardada = repository.save(entity);
        auditoriaContext.setDetalle(
                "Evento ID " + id + " -> estado " + nuevoEstado.toUpperCase());
        return toResponse(guardada);
    }

    @Auditable(accion = "ELIMINAR_EVENTO_PERIODO")
    public void eliminar(Long id) {
        EmpleadoEvento entity = buscar(id);
        entity.setActivo(0);
        repository.save(entity);
        auditoriaContext.setDetalle("Evento eliminado ID: " + id);
    }

    private TipoEvento validar(EventoPeriodoDto dto, Long idActual) {
        if (dto == null) {
            throw new NegocioException("Datos del evento requeridos");
        }
        if (dto.getEmpleadoId() == null) {
            throw new NegocioException("El evento requiere empleadoId");
        }
        if (dto.getTipoEventoId() == null) {
            throw new NegocioException("El evento requiere tipoEventoId");
        }
        TipoEvento tipo = tipoRepository.findById(dto.getTipoEventoId())
                .orElseThrow(() -> new NegocioException(
                        "Tipo de evento no existe: " + dto.getTipoEventoId()));
        if (tipo.getActivo() == null || tipo.getActivo() != 1) {
            throw new NegocioException(
                    "Tipo de evento inactivo: " + tipo.getCodigo());
        }

        if (esMaternidad(tipo)) {
            if (dto.getFechaInicio() == null) {
                throw new NegocioException(
                        "El evento requiere fecha de inicio y fin");
            }
            normalizarMaternidad(dto);
            MaternidadEventoValidator.validar(dto);
        } else {
            if (dto.getFechaInicio() == null || dto.getFechaFin() == null) {
                throw new NegocioException(
                        "El evento requiere fecha de inicio y fin");
            }
            if (dto.getFechaFin().isBefore(dto.getFechaInicio())) {
                throw new NegocioException(
                        "La fecha fin no puede ser anterior a la fecha inicio");
            }
            if (dto.getDiasAfectos() != null && dto.getDiasAfectos() < 0) {
                throw new NegocioException(
                        "Los días afectos no pueden ser negativos");
            }
        }

        if ("S".equalsIgnoreCase(tipo.getRequiereAdjunto())
                && dto.getSustentoLegajoDocId() == null) {
            throw new NegocioException(
                    "El tipo de evento '" + tipo.getCodigo()
                            + "' requiere sustento documental adjunto.");
        }
        if (!"S".equalsIgnoreCase(tipo.getPermiteSolape())) {
            validarSinSolape(dto, idActual, tipo);
        }
        return tipo;
    }

    private void normalizarMaternidad(EventoPeriodoDto dto) {
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(
                dto.getFechaInicio(), dto.getDuracionLegal());
        dto.setFechaFin(fin);
        dto.setDiasAfectos(dto.getDuracionLegal());
        if (dto.getDistribucionMensual() == null || dto.getDistribucionMensual().isEmpty()) {
            dto.setDistribucionMensual(
                    DistribucionMensualCalculator.calcular(dto.getFechaInicio(), fin));
        }
    }

    private void validarSinSolape(EventoPeriodoDto dto, Long idActual, TipoEvento tipo) {
        List<EmpleadoEvento> solapados = repository.findSolapados(
                dto.getEmpleadoId(),
                dto.getFechaInicio(),
                dto.getFechaFin(),
                idActual);
        if (!solapados.isEmpty()) {
            EmpleadoEvento primero = solapados.get(0);
            throw new NegocioException(
                    "El evento '" + tipo.getCodigo() + "' solapa con el evento "
                            + "ID " + primero.getId() + " (" + primero.getFechaInicio()
                            + " → " + primero.getFechaFin() + "). "
                            + "El tipo no permite solape.");
        }
    }

    private EmpleadoEvento mapearEntidad(
            EmpleadoEvento entity,
            EventoPeriodoDto dto,
            TipoEvento tipo) {
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setTipoEventoId(tipo.getId());
        entity.setPeriodo(resolverPeriodo(dto));
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setDiasAfectos(resolverDiasAfectos(dto, tipo));
        entity.setSustentoLegajoDocId(dto.getSustentoLegajoDocId());
        entity.setObservacion(dto.getObservacion());
        if (esMaternidad(tipo)) {
            entity.setDuracionLegal(dto.getDuracionLegal());
            entity.setMotivoExtension(dto.getMotivoExtension());
            entity.setFechaProbableParto(dto.getFechaProbableParto());
            entity.setDifierePrenatalPostnatal(dto.getDifierePrenatalPostnatal());
            entity.setTipoDocumento(dto.getTipoDocumento());
            entity.setNroCitt(dto.getNroCitt());
            entity.setFechaEmisionDoc(dto.getFechaEmisionDoc());
        } else {
            entity.setDuracionLegal(null);
            entity.setMotivoExtension(null);
            entity.setFechaProbableParto(null);
            entity.setDifierePrenatalPostnatal(null);
            entity.setTipoDocumento(null);
            entity.setNroCitt(null);
            entity.setFechaEmisionDoc(null);
        }
        return entity;
    }

    private void persistirDistribucion(
            Long eventoId,
            EventoPeriodoDto dto,
            boolean maternidad) {
        if (!maternidad || dto.getDistribucionMensual() == null) {
            return;
        }
        for (EventoDistribucionMesDto tramo : dto.getDistribucionMensual()) {
            EventoDistribucionMes row = new EventoDistribucionMes();
            row.setEmpleadoEventoId(eventoId);
            row.setPeriodo(tramo.getPeriodo());
            row.setFechaDesde(tramo.getFechaDesde());
            row.setFechaHasta(tramo.getFechaHasta());
            row.setDiasSubsidio(tramo.getDiasSubsidio());
            row.setAfectaDiasLaborados(
                    tramo.getAfectaDiasLaborados() != null
                            ? tramo.getAfectaDiasLaborados() : "S");
            row.setEstadoTramo(
                    tramo.getEstadoTramo() != null
                            ? tramo.getEstadoTramo() : "PENDIENTE_IMPUTACION");
            row.setCreatedAt(LocalDateTime.now());
            distribucionRepository.save(row);
        }
    }

    private EmpleadoEvento buscar(Long id) {
        if (id == null) {
            throw new NegocioException("ID de evento requerido");
        }
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException(
                        "Evento del período no encontrado: " + id));
    }

    private String resolverPeriodo(EventoPeriodoDto dto) {
        if (dto.getPeriodo() != null && !dto.getPeriodo().isBlank()) {
            return dto.getPeriodo().replace("-", "").trim();
        }
        var f = dto.getFechaInicio();
        return String.format("%04d%02d", f.getYear(), f.getMonthValue());
    }

    private Integer resolverDiasAfectos(EventoPeriodoDto dto, TipoEvento tipo) {
        if (esMaternidad(tipo)) {
            return dto.getDuracionLegal();
        }
        if (dto.getDiasAfectos() != null) {
            return dto.getDiasAfectos();
        }
        long dias = ChronoUnit.DAYS.between(
                dto.getFechaInicio(), dto.getFechaFin()) + 1;
        return (int) dias;
    }

    private EventoPeriodoResponseDto toResponse(EmpleadoEvento e) {
        return toResponse(e, Map.of());
    }

    private EventoPeriodoResponseDto toResponse(
            EmpleadoEvento e,
            Map<Long, String[]> personasPorEmpleado) {
        EventoPeriodoResponseDto dto = new EventoPeriodoResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        enriquecerPersona(dto, e.getEmpleadoId(), personasPorEmpleado);
        dto.setTipoEventoId(e.getTipoEventoId());
        dto.setPeriodo(e.getPeriodo());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFin(e.getFechaFin());
        dto.setDiasAfectos(e.getDiasAfectos());
        dto.setSustentoLegajoDocId(e.getSustentoLegajoDocId());
        dto.setObservacion(e.getObservacion());
        dto.setEstado(e.getEstado());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setCreatedBy(e.getCreatedBy());
        dto.setDuracionLegal(e.getDuracionLegal());
        dto.setMotivoExtension(e.getMotivoExtension());
        dto.setFechaProbableParto(e.getFechaProbableParto());
        dto.setDifierePrenatalPostnatal(e.getDifierePrenatalPostnatal());
        dto.setTipoDocumento(e.getTipoDocumento());
        dto.setNroCitt(e.getNroCitt());
        dto.setFechaEmisionDoc(e.getFechaEmisionDoc());

        TipoEvento tipo = resolverTipoEvento(e.getTipoEventoId());
        if (tipo != null) {
            dto.setTipoEventoCodigo(tipo.getCodigo());
            dto.setTipoEventoNombre(tipo.getNombre());
            dto.setGeneraSubsidio(tipo.getGeneraSubsidio());
            dto.setRequiereAdjunto(tipo.getRequiereAdjunto());
        }
        if (e.getId() != null) {
            dto.setDistribucionMensual(
                    distribucionRepository.findByEmpleadoEventoIdOrderByFechaDesdeAsc(e.getId())
                            .stream()
                            .map(this::toDistribucionDto)
                            .toList());
        }
        return dto;
    }

    private EventoDistribucionMesDto toDistribucionDto(EventoDistribucionMes row) {
        EventoDistribucionMesDto dto = new EventoDistribucionMesDto();
        dto.setPeriodo(row.getPeriodo());
        dto.setFechaDesde(row.getFechaDesde());
        dto.setFechaHasta(row.getFechaHasta());
        dto.setDiasSubsidio(row.getDiasSubsidio());
        dto.setAfectaDiasLaborados(row.getAfectaDiasLaborados());
        dto.setEstadoTramo(row.getEstadoTramo());
        return dto;
    }

    private boolean esMaternidad(TipoEvento tipo) {
        return tipo != null && CODIGO_MATERNIDAD.equalsIgnoreCase(tipo.getCodigo());
    }

    private void enriquecerPersona(
            EventoPeriodoResponseDto dto,
            Long empleadoId,
            Map<Long, String[]> personasPorEmpleado) {
        if (empleadoId == null || personasPorEmpleado == null) {
            return;
        }
        String[] info = personasPorEmpleado.get(empleadoId);
        if (info == null) {
            info = resolverPersonaEmpleado(empleadoId);
        }
        if (info != null) {
            dto.setEmpleadoNombre(info[0]);
            dto.setEmpleadoDni(info[1]);
        }
    }

    private Map<Long, String[]> construirCachePersonas(List<EmpleadoEvento> eventos) {
        List<Long> empleadoIds = eventos.stream()
                .map(EmpleadoEvento::getEmpleadoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (empleadoIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String[]> cache = new HashMap<>();
        for (Object[] row : empleadoRepository.findPersonaResumenByEmpleadoIds(empleadoIds)) {
            Long empId = (Long) row[0];
            cache.put(empId, new String[] {
                    (String) row[1],
                    (String) row[2],
            });
        }
        return cache;
    }

    private String[] resolverPersonaEmpleado(Long empleadoId) {
        return empleadoRepository.findPersonaResumenByEmpleadoIds(List.of(empleadoId))
                .stream()
                .findFirst()
                .map(row -> new String[] { (String) row[1], (String) row[2] })
                .orElse(new String[] { null, null });
    }

    /** Evita LazyInitializationException (open-in-view=false). */
    private TipoEvento resolverTipoEvento(Long tipoEventoId) {
        if (tipoEventoId == null) {
            return null;
        }
        return tipoRepository.findById(tipoEventoId).orElse(null);
    }

    private String detalleAuditoria(TipoEvento tipo, EventoPeriodoDto dto) {
        String base = "Evento creado — empleado " + dto.getEmpleadoId()
                + ", tipo " + tipo.getCodigo()
                + ", " + dto.getFechaInicio() + " → " + dto.getFechaFin();
        if (esMaternidad(tipo) && dto.getDistribucionMensual() != null) {
            return base + ", duración " + dto.getDuracionLegal()
                    + ", periodos " + dto.getDistribucionMensual().size();
        }
        return base;
    }
}
