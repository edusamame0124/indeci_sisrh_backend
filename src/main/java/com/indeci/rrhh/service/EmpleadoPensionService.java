package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoPensionDto;
import com.indeci.rrhh.dto.EmpleadoPensionResponseDto;
import com.indeci.rrhh.entity.EmpleadoPension;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoPensionService {

    private final EmpleadoPensionRepository repository;
    private final AuditoriaContext auditoriaContext;

    // ============================
    // CREAR
    // ============================
    @Auditable(accion = "CREAR_PENSION")
    public void guardar(EmpleadoPensionDto dto) {

        // 🔥 VALIDACIÓN TIPO
        if (dto.getTipo() == null || dto.getTipo().isBlank()) {
            auditoriaContext.setDetalle("Tipo de pensión no enviado");
            throw new NegocioException("Debe indicar tipo de pensión (AFP/ONP)");
        }

        // 🔥 NORMALIZAR (opcional pero PRO)
        dto.setTipo(dto.getTipo().toUpperCase());

        // 🔥 VALIDAR VALORES PERMITIDOS
        if (!dto.getTipo().equals("AFP") && !dto.getTipo().equals("ONP")) {
            auditoriaContext.setDetalle("Tipo inválido: " + dto.getTipo());
            throw new NegocioException("Tipo de pensión inválido (solo AFP o ONP)");
        }

        // 🔥 VALIDACIÓN EXISTENTE
        Optional<EmpleadoPension> existente =
                repository.findFirstByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1);

        if (existente.isPresent()) {
            auditoriaContext.setDetalle("Ya existe pensión activa para empleado: " + dto.getEmpleadoId());
            throw new NegocioException("Ya existe una pensión activa");
        }

        // 🔹 guardar normal
        EmpleadoPension entity = new EmpleadoPension();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setAfpId(dto.getAfpId());
        entity.setTipo(dto.getTipo());
        entity.setCuspp(dto.getCuspp());
        entity.setPorcentajeAporte(dto.getPorcentajeAporte());
        entity.setPorcentajeComision(dto.getPorcentajeComision());
        entity.setPorcentajeSeguro(dto.getPorcentajeSeguro());
        entity.setActivo(1);
        entity.setFechaInicio(LocalDate.now());
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Pensión creada para empleado: " + dto.getEmpleadoId());
    }
    // ============================
    // LISTAR
    // ============================
    public List<EmpleadoPensionResponseDto> listar(Long empleadoId) {

        return repository.findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .map(e -> {
                    EmpleadoPensionResponseDto dto = new EmpleadoPensionResponseDto();
                    dto.setId(e.getId());
                    dto.setAfpId(e.getAfpId());
                    dto.setTipo(e.getTipo());
                    dto.setCuspp(e.getCuspp());
                    dto.setPorcentajeAporte(e.getPorcentajeAporte());
                    dto.setActivo(e.getActivo());
                    return dto;
                }).toList();
    }

    // ============================
    // ACTUALIZAR
    // ============================
    @Auditable(accion = "ACTUALIZAR_PENSION")
    public void actualizar(Long id, EmpleadoPensionDto dto) {

        EmpleadoPension entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Pensión no encontrada"));
        
        if (dto.getTipo() == null || dto.getTipo().isBlank()) {
            throw new NegocioException("Debe indicar tipo de pensión");
        }

        dto.setTipo(dto.getTipo().toUpperCase());

        if (!dto.getTipo().equals("AFP") && !dto.getTipo().equals("ONP")) {
            throw new NegocioException("Tipo inválido");
        }

        entity.setAfpId(dto.getAfpId());
        entity.setTipo(dto.getTipo());
        entity.setCuspp(dto.getCuspp());
        entity.setPorcentajeAporte(dto.getPorcentajeAporte());
        entity.setPorcentajeComision(dto.getPorcentajeComision());
        entity.setPorcentajeSeguro(dto.getPorcentajeSeguro());

        repository.save(entity);

        auditoriaContext.setDetalle("Pensión actualizada ID: " + id);
    }

    // ============================
    // ELIMINAR (LÓGICO)
    // ============================
    @Auditable(accion = "ELIMINAR_PENSION")
    public void eliminar(Long id) {

        EmpleadoPension entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Pensión no encontrada"));

        entity.setActivo(0);
        entity.setFechaFin(LocalDate.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Pensión desactivada ID: " + id);
    }
}