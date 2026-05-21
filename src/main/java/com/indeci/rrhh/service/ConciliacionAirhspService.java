package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ConciliacionAirhspDto;
import com.indeci.rrhh.dto.ConciliacionAirhspResponseDto;
import com.indeci.rrhh.dto.ConciliacionRevisionDto;
import com.indeci.rrhh.entity.ConciliacionAirhsp;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.repository.ConciliacionAirhspRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spec 010 / M13 — Conciliación monto sistema vs monto AIRHSP (SPEC §10.3).
 *
 * Estados: PENDIENTE → {CONCILIADO | JUSTIFICADO | RECHAZADO}.
 * Al registrar: si la diferencia es ~0 nace CONCILIADO; si no, PENDIENTE.
 * La columna DIFERENCIA es VIRTUAL en BD — el servicio nunca la escribe.
 */
@Service
@RequiredArgsConstructor
public class ConciliacionAirhspService {

    private static final String EST_PENDIENTE  = "PENDIENTE";
    private static final String EST_CONCILIADO = "CONCILIADO";
    private static final String EST_JUSTIFICADO = "JUSTIFICADO";
    private static final String EST_RECHAZADO  = "RECHAZADO";
    private static final Set<String> ESTADOS_REVISION =
            Set.of(EST_CONCILIADO, EST_JUSTIFICADO, EST_RECHAZADO);

    /** Tolerancia de diferencia para considerar conciliado (SPEC §12.2 PANTALLA-06). */
    private static final double TOLERANCIA = 0.01;

    private final ConciliacionAirhspRepository repository;
    private final EmpleadoRepository empleadoRepository;
    private final AuditoriaContext auditoriaContext;

    // ============================ REGISTRAR ============================
    @Auditable(accion = "REGISTRAR_CONCILIACION_AIRHSP")
    public void registrar(ConciliacionAirhspDto dto) {
        if (dto.getEmpleadoId() == null || dto.getMovimientoPlanillaId() == null
                || dto.getPeriodoPlanillaId() == null) {
            throw new NegocioException(
                    "La conciliación requiere empleadoId, movimientoPlanillaId y periodoPlanillaId");
        }
        if (dto.getMontoSistema() == null || dto.getMontoAirhsp() == null) {
            throw new NegocioException("La conciliación requiere montoSistema y montoAirhsp");
        }
        // Una sola conciliación por (movimiento, empleado) — coincide con la UK de BD.
        repository.findByMovimientoPlanillaIdAndEmpleadoId(
                        dto.getMovimientoPlanillaId(), dto.getEmpleadoId())
                .ifPresent(c -> {
                    throw new NegocioException(
                            "Ya existe una conciliación para ese empleado y movimiento");
                });

        double diferencia = dto.getMontoSistema() - dto.getMontoAirhsp();
        boolean cuadra = Math.abs(diferencia) <= TOLERANCIA;

        ConciliacionAirhsp entity = new ConciliacionAirhsp();
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setMovimientoPlanillaId(dto.getMovimientoPlanillaId());
        entity.setPeriodoPlanillaId(dto.getPeriodoPlanillaId());
        entity.setMontoSistema(dto.getMontoSistema());
        entity.setMontoAirhsp(dto.getMontoAirhsp());
        // DIFERENCIA: columna VIRTUAL — NO se setea.
        entity.setEstado(cuadra ? EST_CONCILIADO : EST_PENDIENTE);
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Conciliación AIRHSP registrada — empleado " + dto.getEmpleadoId()
                        + ", estado " + entity.getEstado());
    }

    // ============================ LISTAR ============================
    public List<ConciliacionAirhspResponseDto> listarPorPeriodo(Long periodoPlanillaId) {
        return repository.findByPeriodoPlanillaId(periodoPlanillaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================ REVISAR ============================
    @Auditable(accion = "REVISAR_CONCILIACION_AIRHSP")
    public void revisar(Long id, ConciliacionRevisionDto dto) {
        ConciliacionAirhsp entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Conciliación no encontrada"));

        String nuevoEstado = dto.getEstado() == null ? null : dto.getEstado().toUpperCase();
        if (nuevoEstado == null || !ESTADOS_REVISION.contains(nuevoEstado)) {
            throw new NegocioException(
                    "Estado de revisión inválido. Use CONCILIADO, JUSTIFICADO o RECHAZADO");
        }
        // JUSTIFICADO y RECHAZADO exigen una justificación escrita.
        boolean exigeJustificacion =
                EST_JUSTIFICADO.equals(nuevoEstado) || EST_RECHAZADO.equals(nuevoEstado);
        if (exigeJustificacion
                && (dto.getJustificacion() == null || dto.getJustificacion().isBlank())) {
            throw new NegocioException(
                    "El estado " + nuevoEstado + " requiere una justificación");
        }

        entity.setEstado(nuevoEstado);
        entity.setJustificacion(dto.getJustificacion());
        entity.setUsuarioRevisa(dto.getUsuarioRevisa());
        entity.setFechaRevision(LocalDate.now());

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Conciliación AIRHSP " + id + " revisada — estado " + nuevoEstado);
    }

    // ============================ HELPER ============================
    private ConciliacionAirhspResponseDto toResponse(ConciliacionAirhsp e) {
        ConciliacionAirhspResponseDto dto = new ConciliacionAirhspResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setRegistroAirhsp(
                empleadoRepository.findById(e.getEmpleadoId())
                        .map(Empleado::getRegistroAirhsp)
                        .orElse(null));
        dto.setMovimientoPlanillaId(e.getMovimientoPlanillaId());
        dto.setPeriodoPlanillaId(e.getPeriodoPlanillaId());
        dto.setMontoSistema(e.getMontoSistema());
        dto.setMontoAirhsp(e.getMontoAirhsp());
        dto.setDiferencia(e.getDiferencia()); // VIRTUAL — poblada por el SELECT
        dto.setEstado(e.getEstado());
        dto.setJustificacion(e.getJustificacion());
        dto.setUsuarioRevisa(e.getUsuarioRevisa());
        dto.setFechaRevision(e.getFechaRevision());
        return dto;
    }
}
