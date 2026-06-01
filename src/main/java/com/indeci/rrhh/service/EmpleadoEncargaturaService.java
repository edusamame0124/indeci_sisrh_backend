package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EncargaturaDto;
import com.indeci.rrhh.dto.EncargaturaResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoEncargatura;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoEncargaturaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;

import lombok.RequiredArgsConstructor;

/**
 * F5.2 — Servicio de encargaturas (titular ↔ reemplazante).
 *
 * <p>Operaciones:</p>
 * <ul>
 *   <li>{@link #listar(String)} — todas, filtrables por estado.</li>
 *   <li>{@link #crear(EncargaturaDto)} — registra con validaciones (titular ≠
 *       encargado, fechaFin >= fechaInicio, no solape activo para el encargado).</li>
 *   <li>{@link #actualizar(Long, EncargaturaDto)} — edita campos (sin cambiar estado).</li>
 *   <li>{@link #cerrar(Long, LocalDate)} — pasa a CULMINADO y setea fechaFin.</li>
 *   <li>{@link #eliminar(Long)} — borrado físico (administrativo).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmpleadoEncargaturaService {

    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_CULMINADO = "CULMINADO";
    public static final String ESTADO_TODOS = "TODOS";

    private final EmpleadoEncargaturaRepository repository;
    private final EmpleadoRepository empleadoRepository;
    private final PersonaRepository personaRepository;

    // ============================ LISTAR ============================

    public List<EncargaturaResponseDto> listar(String estado) {
        List<EmpleadoEncargatura> rows;
        String estadoNorm = estado == null ? null : estado.trim().toUpperCase();
        if (estadoNorm == null || estadoNorm.isEmpty() || ESTADO_TODOS.equals(estadoNorm)) {
            rows = repository.findAllByOrderByFechaInicioDesc();
        } else if (ESTADO_ACTIVO.equals(estadoNorm) || ESTADO_CULMINADO.equals(estadoNorm)) {
            rows = repository.findByEstadoOrderByFechaInicioDesc(estadoNorm);
        } else {
            throw new NegocioException(
                    "Estado inválido. Usa ACTIVO, CULMINADO o TODOS.");
        }

        // Cache de personas para evitar N+1.
        Map<Long, String[]> personaPorEmp = construirCachePersonas(rows);

        List<EncargaturaResponseDto> out = new ArrayList<>(rows.size());
        for (EmpleadoEncargatura e : rows) {
            out.add(mapear(e, personaPorEmp));
        }
        return out;
    }

    // ============================ CREAR ============================

    public EncargaturaResponseDto crear(EncargaturaDto dto) {
        validarRequest(dto, null);

        EmpleadoEncargatura nuevo = new EmpleadoEncargatura();
        nuevo.setEmpleadoTitularId(dto.getEmpleadoTitularId());
        nuevo.setEmpleadoEncargId(dto.getEmpleadoEncargId());
        nuevo.setFechaInicio(dto.getFechaInicio());
        nuevo.setFechaFin(dto.getFechaFin());
        nuevo.setResolucion(dto.getResolucion());
        nuevo.setEstado(ESTADO_ACTIVO);
        nuevo.setCreatedAt(LocalDateTime.now());

        EmpleadoEncargatura saved = repository.save(nuevo);
        return mapearUno(saved);
    }

    // ============================ ACTUALIZAR ============================

    public EncargaturaResponseDto actualizar(Long id, EncargaturaDto dto) {
        EmpleadoEncargatura entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Encargatura no encontrada"));
        validarRequest(dto, id);

        entity.setEmpleadoTitularId(dto.getEmpleadoTitularId());
        entity.setEmpleadoEncargId(dto.getEmpleadoEncargId());
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setResolucion(dto.getResolucion());

        return mapearUno(repository.save(entity));
    }

    // ============================ CERRAR ============================

    public EncargaturaResponseDto cerrar(Long id, LocalDate fechaFin) {
        EmpleadoEncargatura entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Encargatura no encontrada"));
        if (ESTADO_CULMINADO.equals(entity.getEstado())) {
            throw new NegocioException("La encargatura ya está culminada.");
        }
        LocalDate cierre = fechaFin != null ? fechaFin : LocalDate.now();
        if (entity.getFechaInicio() != null && cierre.isBefore(entity.getFechaInicio())) {
            throw new NegocioException(
                    "La fecha de cierre no puede ser anterior a la fecha de inicio.");
        }
        entity.setFechaFin(cierre);
        entity.setEstado(ESTADO_CULMINADO);
        return mapearUno(repository.save(entity));
    }

    // ============================ ELIMINAR ============================

    public void eliminar(Long id) {
        EmpleadoEncargatura entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Encargatura no encontrada"));
        repository.delete(entity);
    }

    // ============================ VALIDACIONES ============================

    private void validarRequest(EncargaturaDto dto, Long idExcluir) {
        if (dto == null) {
            throw new NegocioException("Datos de encargatura requeridos.");
        }
        if (dto.getEmpleadoTitularId() == null) {
            throw new NegocioException("Selecciona al empleado titular.");
        }
        if (dto.getEmpleadoEncargId() == null) {
            throw new NegocioException("Selecciona al empleado encargado.");
        }
        if (dto.getEmpleadoTitularId().equals(dto.getEmpleadoEncargId())) {
            throw new NegocioException(
                    "El titular y el encargado deben ser personas distintas.");
        }
        if (dto.getFechaInicio() == null) {
            throw new NegocioException("Ingresa la fecha de inicio.");
        }
        if (dto.getFechaFin() != null && dto.getFechaFin().isBefore(dto.getFechaInicio())) {
            throw new NegocioException(
                    "La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        // Solape: la encargatura nueva no debe solapar con otra ACTIVA del mismo encargado.
        LocalDate fin = dto.getFechaFin() != null ? dto.getFechaFin() : LocalDate.of(9999, 12, 31);
        List<EmpleadoEncargatura> solapes = repository.findSolapesActivos(
                dto.getEmpleadoEncargId(), dto.getFechaInicio(), fin, idExcluir);
        if (!solapes.isEmpty()) {
            throw new NegocioException(
                    "El empleado encargado ya tiene una encargatura activa que se solapa con estas fechas.");
        }
    }

    // ============================ MAPPER ============================

    private EncargaturaResponseDto mapearUno(EmpleadoEncargatura e) {
        Map<Long, String[]> cache = construirCachePersonas(List.of(e));
        return mapear(e, cache);
    }

    private EncargaturaResponseDto mapear(EmpleadoEncargatura e, Map<Long, String[]> personaPorEmp) {
        EncargaturaResponseDto dto = new EncargaturaResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoTitularId(e.getEmpleadoTitularId());
        dto.setEmpleadoEncargId(e.getEmpleadoEncargId());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFin(e.getFechaFin());
        dto.setResolucion(e.getResolucion());
        dto.setEstado(e.getEstado());
        String[] titularInfo = personaPorEmp.get(e.getEmpleadoTitularId());
        if (titularInfo != null) {
            dto.setTitularNombre(titularInfo[0]);
            dto.setTitularDni(titularInfo[1]);
        }
        String[] encargInfo = personaPorEmp.get(e.getEmpleadoEncargId());
        if (encargInfo != null) {
            dto.setEncargadoNombre(encargInfo[0]);
            dto.setEncargadoDni(encargInfo[1]);
        }
        return dto;
    }

    private Map<Long, String[]> construirCachePersonas(List<EmpleadoEncargatura> rows) {
        Map<Long, String[]> out = new HashMap<>();
        for (EmpleadoEncargatura e : rows) {
            for (Long empId : new Long[] { e.getEmpleadoTitularId(), e.getEmpleadoEncargId() }) {
                if (empId == null || out.containsKey(empId)) continue;
                Empleado emp = empleadoRepository.findById(empId).orElse(null);
                if (emp == null || emp.getPersonaId() == null) {
                    out.put(empId, new String[] { null, null });
                    continue;
                }
                Persona persona = personaRepository.findById(emp.getPersonaId()).orElse(null);
                if (persona == null) {
                    out.put(empId, new String[] { null, null });
                } else {
                    out.put(empId, new String[] { persona.getNombreCompleto(), persona.getDni() });
                }
            }
        }
        return out;
    }
}
