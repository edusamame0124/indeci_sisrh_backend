package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.Suspension4taRequestDto;
import com.indeci.rrhh.dto.Suspension4taResponseDto;
import com.indeci.rrhh.dto.Suspension4taVigenteDto;
import com.indeci.rrhh.entity.Suspension4ta;
import com.indeci.rrhh.repository.Suspension4taRepository;

import lombok.RequiredArgsConstructor;

/**
 * FASE 1 — Gestión de constancias SUNAT de suspensión de retención de 4ta
 * categoría (régimen CAS). CRUD con baja lógica (ESTADO ACTIVO|ANULADO) y
 * consulta de vigencia por fecha de devengue para el motor de planilla.
 *
 * <p>Naturaleza tributaria — NO confundir con {@code SuspensionService}
 * (suspensión laboral/licencia para PLAME).</p>
 */
@Service
@RequiredArgsConstructor
public class Suspension4taService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_ANULADO = "ANULADO";

    private final Suspension4taRepository repository;
    private final AuditoriaContext auditoriaContext;

    // ======================================================================
    // CONSULTA DE VIGENCIA (la consume el motor — solo lectura)
    // ======================================================================

    /**
     * Resuelve si el empleado tiene una constancia de suspensión de 4ta vigente
     * en {@code fechaDevengue}.
     *
     * @return {@link Suspension4taVigenteDto} con {@code vigente=true} si la
     *         cubre; {@code existeVencida=true} si hay constancia activa pero
     *         su vigencia ya pasó; {@link Suspension4taVigenteDto#noRegistrada()}
     *         si el empleado no tiene constancias.
     */
    public Suspension4taVigenteDto consultarVigente(Long empleadoId, LocalDate fechaDevengue) {
        if (empleadoId == null || fechaDevengue == null) {
            return Suspension4taVigenteDto.noRegistrada();
        }

        List<Suspension4ta> vigentes = repository.findVigentes(empleadoId, fechaDevengue);
        if (!vigentes.isEmpty()) {
            Suspension4ta s = vigentes.get(0);
            return new Suspension4taVigenteDto(
                    true, false, s.getNroConstancia(), s.getFechaEmision(), s.getFechaVigFin());
        }

        List<Suspension4ta> activas = repository
                .findByEmpleadoIdAndEstadoOrderByFechaVigIniDesc(empleadoId, ESTADO_ACTIVO);
        if (activas.isEmpty()) {
            return Suspension4taVigenteDto.noRegistrada();
        }
        boolean existeVencida = activas.stream()
                .anyMatch(s -> s.getFechaVigFin() != null
                        && s.getFechaVigFin().isBefore(fechaDevengue));
        return new Suspension4taVigenteDto(false, existeVencida, null, null, null);
    }

    // ======================================================================
    // CRUD
    // ======================================================================

    public List<Suspension4taResponseDto> listarPorEmpleado(Long empleadoId) {
        return repository
                .findByEmpleadoIdAndEstadoOrderByFechaVigIniDesc(empleadoId, ESTADO_ACTIVO)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Auditable(accion = "CREAR_SUSPENSION_4TA")
    public Suspension4taResponseDto crear(Suspension4taRequestDto dto) {
        validar(dto, null);

        Suspension4ta e = new Suspension4ta();
        e.setEmpleadoId(dto.getEmpleadoId());
        e.setNroConstancia(dto.getNroConstancia());
        e.setFechaEmision(dto.getFechaEmision());
        e.setFechaVigIni(dto.getFechaVigIni());
        e.setFechaVigFin(dto.getFechaVigFin());
        e.setObservacion(dto.getObservacion());
        e.setLegajoDocumentoId(dto.getLegajoDocumentoId());
        e.setEstado(ESTADO_ACTIVO);
        e.setCreatedAt(LocalDateTime.now());

        Suspension4ta guardada = repository.save(e);
        auditoriaContext.setDetalle("Suspensión 4ta creada — empleado "
                + dto.getEmpleadoId() + ", constancia " + dto.getNroConstancia());
        return toResponse(guardada);
    }

    @Auditable(accion = "ACTUALIZAR_SUSPENSION_4TA")
    public Suspension4taResponseDto actualizar(Long id, Suspension4taRequestDto dto) {
        Suspension4ta e = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Constancia de suspensión 4ta no encontrada"));
        validar(dto, id);

        e.setNroConstancia(dto.getNroConstancia());
        e.setFechaEmision(dto.getFechaEmision());
        e.setFechaVigIni(dto.getFechaVigIni());
        e.setFechaVigFin(dto.getFechaVigFin());
        e.setObservacion(dto.getObservacion());
        e.setLegajoDocumentoId(dto.getLegajoDocumentoId());
        e.setUpdatedAt(LocalDateTime.now());

        Suspension4ta guardada = repository.save(e);
        auditoriaContext.setDetalle("Suspensión 4ta actualizada ID: " + id);
        return toResponse(guardada);
    }

    @Auditable(accion = "ANULAR_SUSPENSION_4TA")
    public void anular(Long id) {
        Suspension4ta e = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Constancia de suspensión 4ta no encontrada"));
        e.setEstado(ESTADO_ANULADO);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        auditoriaContext.setDetalle("Suspensión 4ta anulada ID: " + id);
    }

    // ======================================================================
    // HELPERS
    // ======================================================================

    /** {@code idActual} excluye la propia fila al editar. */
    private void validar(Suspension4taRequestDto dto, Long idActual) {
        if (dto.getEmpleadoId() == null) {
            throw new NegocioException("La constancia requiere empleadoId");
        }
        if (dto.getFechaVigIni() == null) {
            throw new NegocioException("La constancia requiere fecha de inicio de vigencia");
        }
        if (dto.getFechaVigFin() != null && dto.getFechaVigFin().isBefore(dto.getFechaVigIni())) {
            throw new NegocioException("La fecha fin de vigencia no puede ser anterior a la de inicio");
        }
        validarSinSolape(dto, idActual);
    }

    /** Una constancia ACTIVA no puede solapar su vigencia con otra del mismo empleado. */
    private void validarSinSolape(Suspension4taRequestDto dto, Long idActual) {
        LocalDate iniNueva = dto.getFechaVigIni();
        LocalDate finNueva = dto.getFechaVigFin(); // null = +∞
        for (Suspension4ta s : repository
                .findByEmpleadoIdAndEstadoOrderByFechaVigIniDesc(dto.getEmpleadoId(), ESTADO_ACTIVO)) {
            if (idActual != null && idActual.equals(s.getId())) {
                continue;
            }
            boolean iniAntesDeFinExistente =
                    finNueva == null || !s.getFechaVigIni().isAfter(finNueva);
            boolean finDespuesDeIniExistente =
                    s.getFechaVigFin() == null || !s.getFechaVigFin().isBefore(iniNueva);
            if (iniAntesDeFinExistente && finDespuesDeIniExistente) {
                throw new NegocioException(
                        "La constancia solapa su vigencia con otra activa del empleado (ID "
                                + s.getId() + ")");
            }
        }
    }

    private Suspension4taResponseDto toResponse(Suspension4ta e) {
        Suspension4taResponseDto dto = new Suspension4taResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setNroConstancia(e.getNroConstancia());
        dto.setFechaEmision(e.getFechaEmision());
        dto.setFechaVigIni(e.getFechaVigIni());
        dto.setFechaVigFin(e.getFechaVigFin());
        dto.setEstado(e.getEstado());
        dto.setObservacion(e.getObservacion());
        dto.setLegajoDocumentoId(e.getLegajoDocumentoId());
        dto.setEstadoVigencia(calcularBadge(e));
        return dto;
    }

    /** Badge para la pantalla: INACTIVA | FUTURA | VIGENTE | VENCIDA. */
    private String calcularBadge(Suspension4ta e) {
        if (ESTADO_ANULADO.equalsIgnoreCase(e.getEstado())) {
            return "INACTIVA";
        }
        LocalDate hoy = LocalDate.now();
        if (e.getFechaVigIni() != null && e.getFechaVigIni().isAfter(hoy)) {
            return "FUTURA";
        }
        if (e.getFechaVigFin() != null && e.getFechaVigFin().isBefore(hoy)) {
            return "VENCIDA";
        }
        return "VIGENTE";
    }
}
