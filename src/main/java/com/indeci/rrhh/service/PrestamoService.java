package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PrestamoDto;
import com.indeci.rrhh.dto.PrestamoResponseDto;
import com.indeci.rrhh.entity.Prestamo;
import com.indeci.rrhh.repository.PrestamoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Préstamos del empleado (SPEC §12.2 PANTALLA-08).
 *
 * Estado: ACTIVO → CANCELADO (cuando se pagan todas las cuotas).
 * El saldo pendiente se deriva: MONTO_TOTAL − CUOTAS_PAGADAS × CUOTA_MENSUAL.
 */
@Service
@RequiredArgsConstructor
public class PrestamoService {

    private static final String EST_ACTIVO    = "ACTIVO";
    private static final String EST_CANCELADO = "CANCELADO";

    private final PrestamoRepository repository;
    private final AuditoriaContext auditoriaContext;

    // ============================ REGISTRAR ============================

    @Auditable(accion = "REGISTRAR_PRESTAMO")
    public void registrar(PrestamoDto dto) {
        if (dto.getEmpleadoId() == null) {
            throw new NegocioException("El préstamo requiere empleadoId");
        }
        if (dto.getDescripcion() == null || dto.getDescripcion().isBlank()) {
            throw new NegocioException("El préstamo requiere una descripción");
        }
        if (dto.getMontoTotal() == null || dto.getMontoTotal() < 0) {
            throw new NegocioException("El monto total del préstamo no puede ser negativo");
        }
        if (dto.getNumeroCuotas() == null || dto.getNumeroCuotas() <= 0) {
            throw new NegocioException("El número de cuotas debe ser mayor a cero");
        }
        if (dto.getCuotaMensual() == null || dto.getCuotaMensual() < 0) {
            throw new NegocioException("La cuota mensual no puede ser negativa");
        }

        Prestamo entity = new Prestamo();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setDescripcion(dto.getDescripcion());
        entity.setMontoTotal(dto.getMontoTotal());
        entity.setNumeroCuotas(dto.getNumeroCuotas());
        entity.setCuotaMensual(dto.getCuotaMensual());
        entity.setCuotasPagadas(0);
        entity.setEstado(EST_ACTIVO);
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setActivo(1);
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Préstamo registrado — empleado " + dto.getEmpleadoId()
                        + ", monto " + dto.getMontoTotal());
    }

    // ============================ LISTAR ============================

    public List<PrestamoResponseDto> listarPorEmpleado(Long empleadoId) {
        return repository.findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ======================= REGISTRAR PAGO DE CUOTA =======================

    /** Suma una cuota pagada; al completar todas, el préstamo pasa a CANCELADO. */
    @Auditable(accion = "REGISTRAR_PAGO_CUOTA")
    public void registrarPago(Long id) {
        Prestamo entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Préstamo no encontrado"));

        if (EST_CANCELADO.equals(entity.getEstado())) {
            throw new NegocioException("El préstamo ya está cancelado");
        }
        if (entity.getCuotasPagadas() >= entity.getNumeroCuotas()) {
            throw new NegocioException("El préstamo ya tiene todas sus cuotas pagadas");
        }

        entity.setCuotasPagadas(entity.getCuotasPagadas() + 1);
        if (entity.getCuotasPagadas().equals(entity.getNumeroCuotas())) {
            entity.setEstado(EST_CANCELADO);
        }

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Pago de cuota registrado — préstamo " + id
                        + " (" + entity.getCuotasPagadas() + "/" + entity.getNumeroCuotas() + ")");
    }

    // ============================ HELPER ============================

    private PrestamoResponseDto toResponse(Prestamo e) {
        PrestamoResponseDto dto = new PrestamoResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setDescripcion(e.getDescripcion());
        dto.setMontoTotal(e.getMontoTotal());
        dto.setNumeroCuotas(e.getNumeroCuotas());
        dto.setCuotaMensual(e.getCuotaMensual());
        dto.setCuotasPagadas(e.getCuotasPagadas());
        dto.setEstado(e.getEstado());
        dto.setFechaInicio(e.getFechaInicio());
        dto.setSaldoPendiente(saldoPendiente(e));
        return dto;
    }

    /** Saldo pendiente derivado, nunca negativo. */
    private double saldoPendiente(Prestamo e) {
        double pagado = e.getCuotasPagadas() * (e.getCuotaMensual() != null ? e.getCuotaMensual() : 0d);
        double saldo = (e.getMontoTotal() != null ? e.getMontoTotal() : 0d) - pagado;
        if (saldo < 0) {
            saldo = 0d;
        }
        return BigDecimal.valueOf(saldo).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
