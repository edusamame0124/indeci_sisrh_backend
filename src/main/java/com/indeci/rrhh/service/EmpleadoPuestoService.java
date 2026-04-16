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
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoPuestoService {

    private final EmpleadoPuestoRepository repository;
    private final AuditoriaContext auditoriaContext;

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
                    dto.setSedeId(e.getSedeId());
                    dto.setOficinaId(e.getOficinaId());
                    dto.setJefeId(e.getJefeId());
                    dto.setActivo(e.getActivo());
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