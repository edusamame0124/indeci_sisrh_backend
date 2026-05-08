package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoPuestoDto;
import com.indeci.rrhh.dto.EmpleadoPuestoResponseDto;
import com.indeci.rrhh.entity.Dependencia;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.EstructuraOrganica;
import com.indeci.rrhh.entity.Nivel;
import com.indeci.rrhh.entity.Oficina;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.Sede;
import com.indeci.rrhh.repository.DependenciaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EstructuraOrganicaRepository;
import com.indeci.rrhh.repository.NivelRepository;
import com.indeci.rrhh.repository.OficinaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.SedeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoPuestoService {

    private final EmpleadoPuestoRepository repository;
    private final AuditoriaContext auditoriaContext;
    private final NivelRepository nivelRepository;

    private final SedeRepository sedeRepository;

    private final OficinaRepository oficinaRepository;

    private final EstructuraOrganicaRepository
            estructuraOrganicaRepository;

    private final DependenciaRepository
            dependenciaRepository;

    private final EmpleadoRepository
            empleadoRepository;

    private final PersonaRepository
            personaRepository;

    // ============================
    // CREAR NUEVO PUESTO (MOVIMIENTO)
    // ============================
    @Auditable(accion = "CAMBIO_PUESTO")
    public void guardar(EmpleadoPuestoDto dto) {

        // 🔥 VALIDAR
        if (dto.getCargo() == null || dto.getCargo().isBlank()) {
            throw new NegocioException("Debe indicar el cargo");
        }

        // 🔥 CERRAR PUESTO ACTUAL
        Optional<EmpleadoPuesto> actual =
                repository.findFirstByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1);

        if (actual.isPresent()) {
            EmpleadoPuesto puestoActual = actual.get();
            puestoActual.setActivo(0);
            puestoActual.setFechaFin(LocalDate.now());

            repository.save(puestoActual);
        }

        // 🔥 CREAR NUEVO
        EmpleadoPuesto nuevo = new EmpleadoPuesto();
        nuevo.setEmpleadoId(dto.getEmpleadoId());
        nuevo.setCargo(dto.getCargo());
        nuevo.setNivelId(dto.getNivelId());
        nuevo.setSedeId(dto.getSedeId());
        nuevo.setOficinaId(dto.getOficinaId());
        nuevo.setEstructuraOrganicaId(
                dto.getEstructuraOrganicaId());

        nuevo.setDependenciaId(
                dto.getDependenciaId());
        nuevo.setJefeId(dto.getJefeId());
        nuevo.setActivo(1);
        nuevo.setFechaInicio(LocalDate.now());
        nuevo.setCreatedAt(LocalDateTime.now());
        

        repository.save(nuevo);

        auditoriaContext.setDetalle("Cambio de puesto empleado: " + dto.getEmpleadoId());
    }

    // ============================
    // LISTAR HISTORIAL
    // ============================
    public List<EmpleadoPuestoResponseDto> listar(Long empleadoId) {

        return repository.findByEmpleadoIdOrderByFechaInicioDesc(empleadoId)
                .stream()
                .map(e -> {
                    EmpleadoPuestoResponseDto dto = new EmpleadoPuestoResponseDto();
                    dto.setId(e.getId());
                    dto.setCargo(e.getCargo());
                    dto.setNivelId(e.getNivelId());
                    if (e.getNivelId() != null) {

                        Nivel nivel =
                                nivelRepository
                                        .findById(
                                                e.getNivelId())
                                        .orElse(null);

                        if (nivel != null) {

                            dto.setNivel(
                                    nivel.getNombre());
                        }
                    }
                    dto.setSedeId(e.getSedeId());
                    if (e.getSedeId() != null) {

                        Sede sede =
                                sedeRepository
                                        .findById(
                                                e.getSedeId())
                                        .orElse(null);

                        if (sede != null) {

                            dto.setSede(
                                    sede.getNombre());
                        }
                    }
                    dto.setOficinaId(e.getOficinaId());
                    if (e.getOficinaId() != null) {

                        Oficina oficina =
                                oficinaRepository
                                        .findById(
                                                e.getOficinaId())
                                        .orElse(null);

                        if (oficina != null) {

                            dto.setOficina(
                                    oficina.getNombre());
                        }
                    }
                    dto.setJefeId(e.getJefeId());
                    if (e.getJefeId() != null) {

                        Empleado jefe =
                                empleadoRepository
                                        .findById(
                                                e.getJefeId())
                                        .orElse(null);

                        if (jefe != null) {

                            Persona personaJefe =
                                    personaRepository
                                            .findById(
                                                    jefe.getPersonaId())
                                            .orElse(null);

                            if (personaJefe != null) {

                                dto.setJefe(
                                        personaJefe
                                                .getNombreCompleto());
                            }
                        }
                    }
                    dto.setActivo(e.getActivo());
                    dto.setEstructuraOrganicaId(
                            e.getEstructuraOrganicaId());
                    if (e.getEstructuraOrganicaId() != null) {

                        EstructuraOrganica estructura =
                                estructuraOrganicaRepository
                                        .findById(
                                                e.getEstructuraOrganicaId())
                                        .orElse(null);

                        if (estructura != null) {

                            dto.setEstructuraOrganica(
                                    estructura.getNombre());
                        }
                    }

                    dto.setDependenciaId(
                            e.getDependenciaId());
                    if (e.getDependenciaId() != null) {

                        Dependencia dependencia =
                                dependenciaRepository
                                        .findById(
                                                e.getDependenciaId())
                                        .orElse(null);

                        if (dependencia != null) {

                            dto.setDependencia(
                                    dependencia.getNombre());
                        }
                    }
                    return dto;
                }).toList();
    }

    // ============================
    // ELIMINAR (NO SE RECOMIENDA)
    // ============================
    @Auditable(accion = "ELIMINAR_PUESTO")
    public void eliminar(Long id) {

        EmpleadoPuesto entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Puesto no encontrado"));

        entity.setActivo(0);
        entity.setFechaFin(LocalDate.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Puesto desactivado ID: " + id);
    }
}