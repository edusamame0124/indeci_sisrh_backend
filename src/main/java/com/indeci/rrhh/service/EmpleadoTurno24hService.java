package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoTurno24hDto;
import com.indeci.rrhh.dto.EmpleadoTurno24hResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoTurno24h;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EmpleadoTurno24hRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Turno continuo 24h por trabajador (M04 Asistencia — guardia COEN).
 * CRUD de vigencia + trazabilidad de autorización (directiva RIS INDECI
 * 2026-08-09). No define horario — eso vive en el régimen "COEN" de
 * {@code INDECI_JORNADA_REGIMEN}.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoTurno24hService {

    private final EmpleadoTurno24hRepository repository;
    private final EmpleadoRepository empleadoRepository;

    @Transactional(readOnly = true)
    public List<EmpleadoTurno24hResponseDto> listarPorEmpleado(Long empleadoId) {
        return repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(empleadoId, 1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmpleadoTurno24hResponseDto> listarTodosVigentes() {
        return repository.findByActivo(1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Auditable(accion = "REGISTRAR_TURNO_24H")
    @Transactional
    public void registrar(EmpleadoTurno24hDto dto) {
        Empleado empleado = empleadoRepository.findById(dto.getEmpleadoId())
                .orElseThrow(() -> new NegocioException("Empleado no existe"));

        validar(dto);
        validarSolapamiento(dto.getEmpleadoId(), dto.getFechaInicio(), dto.getFechaFin(), null);

        EmpleadoTurno24h entity = new EmpleadoTurno24h();
        entity.setEmpleadoId(empleado.getId());
        aplicarCampos(entity, dto);
        entity.setActivo(1);
        entity.setCreatedBy(usuarioActual());
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);
    }

    @Auditable(accion = "ACTUALIZAR_TURNO_24H")
    @Transactional
    public void actualizar(Long id, EmpleadoTurno24hDto dto) {
        EmpleadoTurno24h entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Turno 24h no encontrado"));

        validar(dto);
        validarSolapamiento(entity.getEmpleadoId(), dto.getFechaInicio(), dto.getFechaFin(), id);

        aplicarCampos(entity, dto);
        entity.setUpdatedBy(usuarioActual());
        entity.setUpdatedAt(LocalDateTime.now());

        repository.save(entity);
    }

    @Auditable(accion = "ELIMINAR_TURNO_24H")
    @Transactional
    public void eliminar(Long id) {
        EmpleadoTurno24h entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Turno 24h no encontrado"));
        entity.setActivo(0);
        entity.setUpdatedBy(usuarioActual());
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    private void aplicarCampos(EmpleadoTurno24h entity, EmpleadoTurno24hDto dto) {
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setDocumentoAutorizacion(dto.getDocumentoAutorizacion().trim());
        entity.setMotivo(trim(dto.getMotivo()));
    }

    private void validar(EmpleadoTurno24hDto dto) {
        if (dto.getEmpleadoId() == null) {
            throw new NegocioException("Debe indicar el empleado.");
        }
        if (esVacio(dto.getDocumentoAutorizacion())) {
            throw new NegocioException(
                    "Debe indicar el documento que autoriza el turno 24h (ej. Resolución Jefatural).");
        }
        if (dto.getFechaInicio() == null || dto.getFechaFin() == null) {
            throw new NegocioException("La vigencia (fecha de inicio y fin) es obligatoria.");
        }
        if (dto.getFechaFin().isBefore(dto.getFechaInicio())) {
            throw new NegocioException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
    }

    /** No permite dos turnos 24h ACTIVOS del mismo empleado con fechas que se crucen. */
    private void validarSolapamiento(Long empleadoId, LocalDate ini, LocalDate fin, Long idExcluir) {
        boolean choca = repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(empleadoId, 1).stream()
                .filter(e -> idExcluir == null || !e.getId().equals(idExcluir))
                .anyMatch(e -> !fin.isBefore(e.getFechaInicio()) && !ini.isAfter(e.getFechaFin()));
        if (choca) {
            throw new NegocioException(
                    "Ya existe un turno 24h activo para este trabajador que se cruza con esas fechas.");
        }
    }

    private String trim(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private boolean esVacio(String s) {
        return s == null || s.isBlank();
    }

    private String usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private EmpleadoTurno24hResponseDto toDto(EmpleadoTurno24h e) {
        EmpleadoTurno24hResponseDto dto = new EmpleadoTurno24hResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFin(e.getFechaFin());
        dto.setDocumentoAutorizacion(e.getDocumentoAutorizacion());
        dto.setMotivo(e.getMotivo());
        dto.setEstadoVigencia(estadoVigencia(e));
        return dto;
    }

    private String estadoVigencia(EmpleadoTurno24h e) {
        LocalDate hoy = LocalDate.now();
        if (hoy.isBefore(e.getFechaInicio())) {
            return "FUTURA";
        }
        if (hoy.isAfter(e.getFechaFin())) {
            return "VENCIDA";
        }
        return "VIGENTE";
    }
}
