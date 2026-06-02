package com.indeci.rrhh.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EventoPeriodoDto;
import com.indeci.rrhh.dto.EventoPeriodoResponseDto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;

import lombok.RequiredArgsConstructor;

/**
 * F2.5 — CRUD de {@code INDECI_EMPLEADO_EVENTO} con validaciones:
 *
 * <ul>
 *   <li>Tipo de evento debe existir y estar activo.</li>
 *   <li>Fechas requeridas y {@code fechaFin >= fechaInicio}.</li>
 *   <li>{@code diasAfectos} se deriva de {@code fechaFin - fechaInicio + 1}
 *       si llega null.</li>
 *   <li>{@code periodo} se deriva de {@code fechaInicio} ({@code "YYYYMM"})
 *       si llega null.</li>
 *   <li>Si {@code tipo.requiereAdjunto = 'S'} → exige
 *       {@code sustentoLegajoDocId} no null.</li>
 *   <li>Si {@code tipo.permiteSolape = 'N'} → llama a
 *       {@code findSolapados} y rechaza si hay traslape con otro evento
 *       activo del mismo empleado.</li>
 *   <li>Estados: REGISTRADO (default) → VALIDADO o RECHAZADO.</li>
 *   <li>Baja lógica vía {@code activo = 0}.</li>
 * </ul>
 *
 * <p>F2.6 conectará el alta con upload Legajo+FTP. F2.4
 * {@code SubsidioCalculadorService} se invoca por separado para eventos
 * con {@code generaSubsidio='S'}.</p>
 */
@Service
@RequiredArgsConstructor
public class EventoPeriodoService {

    private static final Set<String> ESTADOS_VALIDOS =
            Set.of("REGISTRADO", "VALIDADO", "RECHAZADO");

    private final EmpleadoEventoRepository repository;
    private final TipoEventoRepository tipoRepository;
    private final AuditoriaContext auditoriaContext;

    // ============================ LISTAR ============================

    public List<EventoPeriodoResponseDto> listarPorEmpleado(Long empleadoId) {
        if (empleadoId == null) {
            throw new NegocioException("empleadoId requerido");
        }
        return repository
                .findByEmpleadoIdAndActivoOrderByFechaInicioDesc(empleadoId, 1)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public EventoPeriodoResponseDto obtener(Long id) {
        return toResponse(buscar(id));
    }

    // ============================ CREAR ============================

    @Auditable(accion = "CREAR_EVENTO_PERIODO")
    public EventoPeriodoResponseDto crear(EventoPeriodoDto dto) {
        TipoEvento tipo = validar(dto, null);

        EmpleadoEvento entity = new EmpleadoEvento();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setTipoEventoId(tipo.getId());
        entity.setPeriodo(resolverPeriodo(dto));
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setDiasAfectos(resolverDiasAfectos(dto));
        entity.setSustentoLegajoDocId(dto.getSustentoLegajoDocId());
        entity.setObservacion(dto.getObservacion());
        entity.setEstado("REGISTRADO");
        entity.setActivo(1);
        entity.setCreatedAt(LocalDateTime.now());

        EmpleadoEvento guardada = repository.save(entity);

        auditoriaContext.setDetalle("Evento creado — empleado " + dto.getEmpleadoId()
                + ", tipo " + tipo.getCodigo()
                + ", " + dto.getFechaInicio() + " → " + dto.getFechaFin());

        return toResponse(guardada);
    }

    // ============================ ACTUALIZAR ============================

    @Auditable(accion = "ACTUALIZAR_EVENTO_PERIODO")
    public EventoPeriodoResponseDto actualizar(Long id, EventoPeriodoDto dto) {
        EmpleadoEvento entity = buscar(id);
        TipoEvento tipo = validar(dto, id);

        entity.setTipoEventoId(tipo.getId());
        entity.setPeriodo(resolverPeriodo(dto));
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setDiasAfectos(resolverDiasAfectos(dto));
        entity.setSustentoLegajoDocId(dto.getSustentoLegajoDocId());
        entity.setObservacion(dto.getObservacion());

        EmpleadoEvento guardada = repository.save(entity);
        auditoriaContext.setDetalle("Evento actualizado ID: " + id);
        return toResponse(guardada);
    }

    // ============================ CAMBIAR ESTADO ============================

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

    // ============================ ELIMINAR (LÓGICO) ============================

    @Auditable(accion = "ELIMINAR_EVENTO_PERIODO")
    public void eliminar(Long id) {
        EmpleadoEvento entity = buscar(id);
        entity.setActivo(0);
        repository.save(entity);
        auditoriaContext.setDetalle("Evento eliminado ID: " + id);
    }

    // ============================ VALIDACIÓN ============================

    /**
     * Valida el DTO y devuelve el {@link TipoEvento}. {@code idActual} permite
     * excluir el propio evento del check de solape al editar.
     */
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

    /**
     * Verifica que el rango {@code [fechaInicio, fechaFin]} no se traslape
     * con ningún otro evento activo del mismo empleado (excepto el propio
     * evento si {@code idActual != null}).
     */
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

    // ============================ HELPERS ============================

    private EmpleadoEvento buscar(Long id) {
        if (id == null) {
            throw new NegocioException("ID de evento requerido");
        }
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException(
                        "Evento del período no encontrado: " + id));
    }

    /**
     * Si {@code dto.periodo} es null, deriva el período {@code "YYYYMM"} desde
     * {@code fechaInicio}. Si viene seteado, lo normaliza eliminando guiones.
     */
    private String resolverPeriodo(EventoPeriodoDto dto) {
        if (dto.getPeriodo() != null && !dto.getPeriodo().isBlank()) {
            return dto.getPeriodo().replace("-", "").trim();
        }
        var f = dto.getFechaInicio();
        return String.format("%04d%02d", f.getYear(), f.getMonthValue());
    }

    /**
     * Si {@code dto.diasAfectos} es null, deriva de
     * {@code fechaFin - fechaInicio + 1} (días naturales del rango).
     */
    private Integer resolverDiasAfectos(EventoPeriodoDto dto) {
        if (dto.getDiasAfectos() != null) {
            return dto.getDiasAfectos();
        }
        long dias = ChronoUnit.DAYS.between(
                dto.getFechaInicio(), dto.getFechaFin()) + 1;
        return (int) dias;
    }

    private EventoPeriodoResponseDto toResponse(EmpleadoEvento e) {
        EventoPeriodoResponseDto dto = new EventoPeriodoResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
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

        // Denormaliza datos del catálogo. Si tipoEvento lazy no se carga
        // (caso fuera de transacción), consultamos por id.
        TipoEvento tipo = e.getTipoEvento();
        if (tipo == null && e.getTipoEventoId() != null) {
            tipo = tipoRepository.findById(e.getTipoEventoId()).orElse(null);
        }
        if (tipo != null) {
            dto.setTipoEventoCodigo(tipo.getCodigo());
            dto.setTipoEventoNombre(tipo.getNombre());
            dto.setGeneraSubsidio(tipo.getGeneraSubsidio());
            dto.setRequiereAdjunto(tipo.getRequiereAdjunto());
        }
        return dto;
    }
}
