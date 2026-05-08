package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoPlanillaDto;
import com.indeci.rrhh.dto.EmpleadoPlanillaResponseDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoPlanillaService {

    private final EmpleadoPlanillaRepository repository;
    private final AuditoriaContext auditoriaContext;

    // ============================
    // CREAR
    // ============================
    @Auditable(accion = "CREAR_PLANILLA")
    public void guardar(EmpleadoPlanillaDto dto) {

        // 🔥 VALIDAR SUELDO
        if (dto.getSueldoBasico() == null || dto.getSueldoBasico() <= 0) {
            throw new NegocioException("Sueldo básico inválido");
        }

        // 🔥 SOLO UNA ACTIVA
        Optional<EmpleadoPlanilla> existente =
                repository.findFirstByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1);

        if (existente.isPresent()) {
            throw new NegocioException("Ya existe planilla activa");
        }

        EmpleadoPlanilla entity = new EmpleadoPlanilla();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setSueldoBasico(dto.getSueldoBasico());
        entity.setMovilidad(dto.getMovilidad());
        entity.setAlimentacion(dto.getAlimentacion());
        entity.setTieneAsignacionFamiliar(dto.getTieneAsignacionFamiliar());
        entity.setNumHijos(dto.getNumHijos());
        entity.setDescuentoBanco(dto.getDescuentoBanco());
        entity.setDescuentoInstitucion(dto.getDescuentoInstitucion());
        entity.setActivo(1);
        entity.setFechaInicio(LocalDate.now());
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Planilla creada empleado: " + dto.getEmpleadoId());
    }

    // ============================
    // LISTAR
    // ============================
    public List<EmpleadoPlanillaResponseDto> listar(Long empleadoId) {

        return repository.findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .map(e -> {
                    EmpleadoPlanillaResponseDto dto = new EmpleadoPlanillaResponseDto();
                    dto.setId(e.getId());
                    dto.setSueldoBasico(e.getSueldoBasico());
                    dto.setMovilidad(e.getMovilidad());
                    dto.setAlimentacion(e.getAlimentacion());
                    dto.setTieneAsignacionFamiliar(e.getTieneAsignacionFamiliar());
                    dto.setNumHijos(e.getNumHijos());
                    dto.setActivo(e.getActivo());
                    dto.setDescuentoBanco(e.getDescuentoBanco());
                    dto.setDescuentoInstitucion(e.getDescuentoInstitucion());
                    
                    return dto;
                }).toList();
    }

    // ============================
    // ACTUALIZAR
    // ============================
    @Auditable(accion = "ACTUALIZAR_PLANILLA")
    public void actualizar(Long id, EmpleadoPlanillaDto dto) {

        EmpleadoPlanilla entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Planilla no encontrada"));

        entity.setSueldoBasico(dto.getSueldoBasico());
        entity.setMovilidad(dto.getMovilidad());
        entity.setAlimentacion(dto.getAlimentacion());
        entity.setTieneAsignacionFamiliar(dto.getTieneAsignacionFamiliar());
        entity.setNumHijos(dto.getNumHijos());
        entity.setDescuentoBanco(dto.getDescuentoBanco());
        entity.setDescuentoInstitucion(dto.getDescuentoInstitucion());

        repository.save(entity);

        auditoriaContext.setDetalle("Planilla actualizada ID: " + id);
    }

    // ============================
    // ELIMINAR (LÓGICO)
    // ============================
    @Auditable(accion = "ELIMINAR_PLANILLA")
    public void eliminar(Long id) {

        EmpleadoPlanilla entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Planilla no encontrada"));

        entity.setActivo(0);
        entity.setFechaFin(LocalDate.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Planilla desactivada ID: " + id);
    }
}