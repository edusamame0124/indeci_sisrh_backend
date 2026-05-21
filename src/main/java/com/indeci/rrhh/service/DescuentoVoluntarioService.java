package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.ConceptoSinCodigoMefException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.DescuentoVoluntarioDto;
import com.indeci.rrhh.dto.DescuentoVoluntarioResponseDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.DescuentoVoluntario;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.DescuentoVoluntarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spec 010 / M07 — Gestión de descuentos voluntarios del empleado (12 tipos
 * SISPER, SPEC §6.4). CRUD con baja lógica (ESTADO ACTIVO|INACTIVO).
 *
 * Validaciones:
 *   - LEY-01: el concepto referenciado debe tener CODIGO_MEF.
 *   - El concepto debe ser de TIPO_CONCEPTO = DESCUENTO.
 *   - El monto mensual debe ser positivo.
 */
@Service
@RequiredArgsConstructor
public class DescuentoVoluntarioService {

    private static final String ESTADO_ACTIVO   = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";

    private final DescuentoVoluntarioRepository repository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final AuditoriaContext auditoriaContext;

    // ============================ CREAR ============================
    @Auditable(accion = "CREAR_DESCUENTO_VOLUNTARIO")
    public void guardar(DescuentoVoluntarioDto dto) {
        ConceptoPlanilla concepto = validarConcepto(dto.getConceptoPlanillaId());
        validarMonto(dto.getMontoMensual());
        if (dto.getEmpleadoId() == null) {
            throw new NegocioException("El descuento voluntario requiere empleadoId");
        }
        if (dto.getFechaInicio() == null) {
            throw new NegocioException("El descuento voluntario requiere fecha de inicio");
        }

        DescuentoVoluntario entity = new DescuentoVoluntario();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setConceptoPlanillaId(concepto.getId());
        entity.setCodigoSisper(concepto.getCodigoSisper()); // espejo del concepto
        entity.setMontoMensual(dto.getMontoMensual());
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setEstado(ESTADO_ACTIVO);
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Descuento voluntario creado — empleado " + dto.getEmpleadoId()
                        + ", concepto " + concepto.getCodigoMef());
    }

    // ============================ LISTAR ============================
    public List<DescuentoVoluntarioResponseDto> listar(Long empleadoId) {
        return repository.findByEmpleadoIdAndEstado(empleadoId, ESTADO_ACTIVO)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================ ACTUALIZAR ============================
    @Auditable(accion = "ACTUALIZAR_DESCUENTO_VOLUNTARIO")
    public void actualizar(Long id, DescuentoVoluntarioDto dto) {
        DescuentoVoluntario entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Descuento voluntario no encontrado"));

        validarMonto(dto.getMontoMensual());

        // El concepto puede cambiar; si viene, se revalida.
        if (dto.getConceptoPlanillaId() != null
                && !dto.getConceptoPlanillaId().equals(entity.getConceptoPlanillaId())) {
            ConceptoPlanilla concepto = validarConcepto(dto.getConceptoPlanillaId());
            entity.setConceptoPlanillaId(concepto.getId());
            entity.setCodigoSisper(concepto.getCodigoSisper());
        }

        entity.setMontoMensual(dto.getMontoMensual());
        if (dto.getFechaInicio() != null) entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());

        repository.save(entity);
        auditoriaContext.setDetalle("Descuento voluntario actualizado ID: " + id);
    }

    // ============================ ELIMINAR (LÓGICO) ============================
    @Auditable(accion = "ELIMINAR_DESCUENTO_VOLUNTARIO")
    public void eliminar(Long id) {
        DescuentoVoluntario entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Descuento voluntario no encontrado"));

        entity.setEstado(ESTADO_INACTIVO);
        if (entity.getFechaFin() == null) {
            entity.setFechaFin(LocalDate.now());
        }
        repository.save(entity);
        auditoriaContext.setDetalle("Descuento voluntario desactivado ID: " + id);
    }

    // ============================ HELPERS ============================

    private ConceptoPlanilla validarConcepto(Long conceptoPlanillaId) {
        if (conceptoPlanillaId == null) {
            throw new NegocioException("El descuento voluntario requiere conceptoPlanillaId");
        }
        ConceptoPlanilla concepto = conceptoRepository.findById(conceptoPlanillaId)
                .orElseThrow(() -> new NegocioException(
                        "Concepto no existe: id=" + conceptoPlanillaId));

        // LEY-01 (Ley 32448)
        if (concepto.getCodigoMef() == null || concepto.getCodigoMef().isBlank()) {
            throw new ConceptoSinCodigoMefException(concepto.getId(), concepto.getNombre());
        }
        // Un descuento voluntario debe apuntar a un concepto de tipo DESCUENTO.
        if (!"DESCUENTO".equalsIgnoreCase(concepto.getTipoConcepto())) {
            throw new NegocioException(
                    "El concepto '" + concepto.getNombre()
                            + "' no es de tipo DESCUENTO (es " + concepto.getTipoConcepto() + ")");
        }
        return concepto;
    }

    private void validarMonto(Double monto) {
        if (monto == null || monto <= 0) {
            throw new NegocioException("El monto mensual del descuento debe ser mayor a 0");
        }
    }

    private DescuentoVoluntarioResponseDto toResponse(DescuentoVoluntario e) {
        DescuentoVoluntarioResponseDto dto = new DescuentoVoluntarioResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setCodigoSisper(e.getCodigoSisper());
        dto.setConceptoPlanillaId(e.getConceptoPlanillaId());
        dto.setMontoMensual(e.getMontoMensual());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setFechaFin(e.getFechaFin());
        dto.setEstado(e.getEstado());

        conceptoRepository.findById(e.getConceptoPlanillaId()).ifPresent(c -> {
            dto.setConceptoNombre(c.getNombre());
            dto.setCodigoMef(c.getCodigoMef());
        });
        return dto;
    }
}
