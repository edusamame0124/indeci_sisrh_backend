package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoOtrosIngresosDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoOtrosIngresos;
import com.indeci.rrhh.repository.EmpleadoOtrosIngresosRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtrosIngresosService {

    private final EmpleadoOtrosIngresosRepository otrosIngresosRepository;
    private final EmpleadoRepository empleadoRepository;

    @Transactional(readOnly = true)
    public EmpleadoOtrosIngresosDto obtenerPorEmpleadoYAno(Long empleadoId, Integer anioFiscal) {
        return otrosIngresosRepository.findByEmpleadoIdAndAnioFiscalAndActivo(empleadoId, anioFiscal, 1)
                .map(this::toDto)
                .orElse(null); // Retorna null si no existe para que el frontend maneje estado vacío
    }

    @Transactional
    public EmpleadoOtrosIngresosDto guardar(EmpleadoOtrosIngresosDto dto) {
        log.info("Guardando otros ingresos para empleado {} en año {}", dto.getEmpleadoId(), dto.getAnioFiscal());
        
        if (dto.getMontoRetenciones().compareTo(dto.getMontoIngresos()) > 0) {
            throw new NegocioException("Las retenciones no pueden ser mayores a los ingresos totales");
        }

        Empleado empleado = empleadoRepository.findById(dto.getEmpleadoId())
                .orElseThrow(() -> new NegocioException("Empleado no encontrado"));

        if (!"ACTIVO".equals(empleado.getEstado())) {
            throw new NegocioException("El empleado debe estar activo para registrar otros ingresos");
        }

        Optional<EmpleadoOtrosIngresos> optExistente = otrosIngresosRepository
                .findByEmpleadoIdAndAnioFiscalAndActivo(dto.getEmpleadoId(), dto.getAnioFiscal(), 1);

        EmpleadoOtrosIngresos entity;
        if (optExistente.isPresent()) {
            entity = optExistente.get();
        } else {
            entity = new EmpleadoOtrosIngresos();
            entity.setEmpleadoId(empleado.getId());
            entity.setAnioFiscal(dto.getAnioFiscal());
            entity.setActivo(1);
        }

        entity.setMontoIngresos(dto.getMontoIngresos());
        entity.setMontoRetenciones(dto.getMontoRetenciones());

        entity = otrosIngresosRepository.save(entity);
        return toDto(entity);
    }

    private EmpleadoOtrosIngresosDto toDto(EmpleadoOtrosIngresos entity) {
        EmpleadoOtrosIngresosDto dto = new EmpleadoOtrosIngresosDto();
        dto.setId(entity.getId());
        dto.setEmpleadoId(entity.getEmpleadoId());
        dto.setAnioFiscal(entity.getAnioFiscal());
        dto.setMontoIngresos(entity.getMontoIngresos());
        dto.setMontoRetenciones(entity.getMontoRetenciones());
        return dto;
    }
}
