package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoJornadaExcepcionDto;
import com.indeci.rrhh.dto.EmpleadoJornadaExcepcionResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoJornadaExcepcion;
import com.indeci.rrhh.repository.EmpleadoJornadaExcepcionRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Horario especial por trabajador (M04 Asistencia — Módulo Vinculación).
 * CRUD del override individual sobre {@code INDECI_JORNADA_REGIMEN}, con
 * vigencia y trazabilidad de autorización (decisión RR.HH. 2026-08-08).
 */
@Service
@RequiredArgsConstructor
public class EmpleadoJornadaExcepcionService {

    private static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final EmpleadoJornadaExcepcionRepository repository;
    private final EmpleadoRepository empleadoRepository;

    @Transactional(readOnly = true)
    public List<EmpleadoJornadaExcepcionResponseDto> listarPorEmpleado(Long empleadoId) {
        return repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(empleadoId, 1)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Auditable(accion = "REGISTRAR_HORARIO_ESPECIAL")
    @Transactional
    public void registrar(EmpleadoJornadaExcepcionDto dto) {
        Empleado empleado = empleadoRepository.findById(dto.getEmpleadoId())
                .orElseThrow(() -> new NegocioException("Empleado no existe"));

        validar(dto);
        validarSolapamiento(dto.getEmpleadoId(), dto.getFechaInicio(), dto.getFechaFin(), null);

        EmpleadoJornadaExcepcion entity = new EmpleadoJornadaExcepcion();
        entity.setEmpleadoId(empleado.getId());
        aplicarCampos(entity, dto);
        entity.setActivo(1);
        entity.setCreatedBy(usuarioActual());
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);
    }

    @Auditable(accion = "ACTUALIZAR_HORARIO_ESPECIAL")
    @Transactional
    public void actualizar(Long id, EmpleadoJornadaExcepcionDto dto) {
        EmpleadoJornadaExcepcion entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Horario especial no encontrado"));

        validar(dto);
        validarSolapamiento(entity.getEmpleadoId(), dto.getFechaInicio(), dto.getFechaFin(), id);

        aplicarCampos(entity, dto);
        entity.setUpdatedBy(usuarioActual());
        entity.setUpdatedAt(LocalDateTime.now());

        repository.save(entity);
    }

    @Auditable(accion = "ELIMINAR_HORARIO_ESPECIAL")
    @Transactional
    public void eliminar(Long id) {
        EmpleadoJornadaExcepcion entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Horario especial no encontrado"));
        entity.setActivo(0);
        entity.setUpdatedBy(usuarioActual());
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    private void aplicarCampos(EmpleadoJornadaExcepcion entity, EmpleadoJornadaExcepcionDto dto) {
        entity.setHoraIngreso(dto.getHoraIngreso().trim());
        entity.setHoraSalida(dto.getHoraSalida().trim());
        entity.setRefrigerioInicio(trim(dto.getRefrigerioInicio()));
        entity.setRefrigerioFin(trim(dto.getRefrigerioFin()));
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setDocumentoAutorizacion(dto.getDocumentoAutorizacion().trim());
        entity.setMotivo(trim(dto.getMotivo()));
    }

    private void validar(EmpleadoJornadaExcepcionDto dto) {
        if (dto.getEmpleadoId() == null) {
            throw new NegocioException("Debe indicar el empleado.");
        }
        if (esVacio(dto.getHoraIngreso())) {
            throw new NegocioException("La hora de ingreso es obligatoria.");
        }
        if (esVacio(dto.getHoraSalida())) {
            throw new NegocioException("La hora de salida es obligatoria.");
        }
        if (esVacio(dto.getDocumentoAutorizacion())) {
            throw new NegocioException(
                    "Debe indicar el documento que autoriza el horario especial (ej. Resolución Jefatural).");
        }
        if (dto.getFechaInicio() == null || dto.getFechaFin() == null) {
            throw new NegocioException("La vigencia (fecha de inicio y fin) es obligatoria.");
        }
        if (dto.getFechaFin().isBefore(dto.getFechaInicio())) {
            throw new NegocioException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }

        validarHora(dto.getHoraIngreso(), "hora de ingreso");
        validarHora(dto.getHoraSalida(), "hora de salida");
        validarHora(dto.getRefrigerioInicio(), "inicio de refrigerio");
        validarHora(dto.getRefrigerioFin(), "fin de refrigerio");

        if (esMayorOIgual(dto.getHoraIngreso(), dto.getHoraSalida())) {
            throw new NegocioException("La hora de salida debe ser posterior a la de ingreso.");
        }
        if (esMayorOIgual(dto.getRefrigerioInicio(), dto.getRefrigerioFin())) {
            throw new NegocioException("El fin de refrigerio debe ser posterior al inicio.");
        }
    }

    /** No permite dos excepciones ACTIVAS del mismo empleado con fechas que se crucen. */
    private void validarSolapamiento(Long empleadoId, LocalDate ini, LocalDate fin, Long idExcluir) {
        boolean choca = repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(empleadoId, 1).stream()
                .filter(e -> idExcluir == null || !e.getId().equals(idExcluir))
                .anyMatch(e -> !fin.isBefore(e.getFechaInicio()) && !ini.isAfter(e.getFechaFin()));
        if (choca) {
            throw new NegocioException(
                    "Ya existe un horario especial activo para este trabajador que se cruza con esas fechas.");
        }
    }

    private void validarHora(String hora, String campo) {
        if (hora != null && !hora.isBlank() && !HORA.matcher(hora.trim()).matches()) {
            throw new NegocioException("Formato inválido en " + campo + " (use HH:mm).");
        }
    }

    /** true si ambas horas existen y la primera es >= la segunda. */
    private boolean esMayorOIgual(String a, String b) {
        Integer ma = toMinutos(a);
        Integer mb = toMinutos(b);
        return ma != null && mb != null && ma >= mb;
    }

    private Integer toMinutos(String hora) {
        if (hora == null || hora.isBlank() || !HORA.matcher(hora.trim()).matches()) {
            return null;
        }
        String[] p = hora.trim().split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
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

    private EmpleadoJornadaExcepcionResponseDto toDto(EmpleadoJornadaExcepcion e) {
        EmpleadoJornadaExcepcionResponseDto dto = new EmpleadoJornadaExcepcionResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setHoraIngreso(e.getHoraIngreso());
        dto.setHoraSalida(e.getHoraSalida());
        dto.setRefrigerioInicio(e.getRefrigerioInicio());
        dto.setRefrigerioFin(e.getRefrigerioFin());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFin(e.getFechaFin());
        dto.setDocumentoAutorizacion(e.getDocumentoAutorizacion());
        dto.setMotivo(e.getMotivo());
        dto.setEstadoVigencia(estadoVigencia(e));
        return dto;
    }

    private String estadoVigencia(EmpleadoJornadaExcepcion e) {
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
