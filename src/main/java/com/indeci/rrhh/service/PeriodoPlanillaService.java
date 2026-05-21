package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AprobacionPeriodoDto;
import com.indeci.rrhh.dto.PeriodoPlanillaDto;
import com.indeci.rrhh.dto.PeriodoPlanillaResponseDto;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.ConciliacionAirhspRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spec 011 — Ciclo de vida del período de planilla (Etapa 3 · B7).
 *
 * Flujo lineal: ABIERTO → EN_REVISION → APROBADO → CERRADO.
 *  - enviarRevision : ABIERTO → EN_REVISION
 *  - aprobar        : EN_REVISION → APROBADO (3 gates duros — LEY-05)
 *  - cerrar         : APROBADO → CERRADO
 *  - reabrir        : retrocede un paso en el flujo
 */
@Service
@RequiredArgsConstructor
public class PeriodoPlanillaService {

    private static final String ABIERTO     = "ABIERTO";
    private static final String EN_REVISION = "EN_REVISION";
    private static final String APROBADO    = "APROBADO";
    private static final String CERRADO     = "CERRADO";

    private final PeriodoPlanillaRepository repository;
    private final ConciliacionAirhspRepository conciliacionRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final AuditoriaContext auditoriaContext;

    // ============================
    // CREAR
    // ============================

    @Auditable(accion = "CREAR_PERIODO_PLANILLA")
    public void guardar(PeriodoPlanillaDto dto) {

        repository.findByPeriodoAndActivo(dto.getPeriodo(), 1)
                .ifPresent(p -> {
                    throw new NegocioException("Periodo ya existe");
                });

        PeriodoPlanilla entity = new PeriodoPlanilla();
        entity.setPeriodo(dto.getPeriodo());
        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());
        entity.setEstado(ABIERTO);
        entity.setObservacion(dto.getObservacion());
        entity.setActivo(1);
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Periodo creado: " + dto.getPeriodo());
    }

    // ============================
    // LISTAR
    // ============================

    public List<PeriodoPlanillaResponseDto> listar() {

        return repository.findByActivo(1)
                .stream()
                .map(e -> {
                    PeriodoPlanillaResponseDto dto = new PeriodoPlanillaResponseDto();
                    dto.setId(e.getId());
                    dto.setPeriodo(e.getPeriodo());
                    dto.setFechaInicio(e.getFechaInicio());
                    dto.setFechaFin(e.getFechaFin());
                    dto.setEstado(e.getEstado());
                    dto.setObservacion(e.getObservacion());
                    dto.setFechaCierre(e.getFechaCierre());
                    dto.setNroCertPresup(e.getNroCertPresup());
                    dto.setFechaAprobacion(e.getFechaAprobacion());
                    dto.setActivo(e.getActivo());
                    return dto;
                })
                .toList();
    }

    // ============================
    // ENVIAR A REVISIÓN  (ABIERTO → EN_REVISION)
    // ============================

    @Auditable(accion = "ENVIAR_REVISION_PERIODO")
    public void enviarRevision(Long id) {
        PeriodoPlanilla entity = obtener(id);
        if (!ABIERTO.equals(entity.getEstado())) {
            throw new NegocioException(
                    "Solo un período ABIERTO puede enviarse a revisión "
                            + "(estado actual: " + entity.getEstado() + ")");
        }
        entity.setEstado(EN_REVISION);
        repository.save(entity);
        auditoriaContext.setDetalle("Periodo " + id + " enviado a revisión");
    }

    // ============================
    // APROBAR  (EN_REVISION → APROBADO) — 3 gates duros (LEY-05)
    // ============================

    @Auditable(accion = "APROBAR_PERIODO_PLANILLA")
    public void aprobar(Long id, AprobacionPeriodoDto dto) {
        PeriodoPlanilla entity = obtener(id);

        if (!EN_REVISION.equals(entity.getEstado())) {
            throw new NegocioException(
                    "Solo un período EN_REVISION puede aprobarse "
                            + "(estado actual: " + entity.getEstado() + ")");
        }

        // Gate 1 — Certificación presupuestal obligatoria (LEY-05 / Ley 28411).
        if (dto == null || dto.getNroCertPresup() == null
                || dto.getNroCertPresup().isBlank()) {
            throw new NegocioException(
                    "No se puede aprobar sin número de certificación "
                            + "presupuestal (Ley 28411 — LEY-05)");
        }

        // Gate 2 — Sin conciliación AIRHSP en ROJO (PENDIENTE / RECHAZADO).
        long conciliacionesRojas = conciliacionRepository
                .findByPeriodoPlanillaId(id)
                .stream()
                .filter(c -> "PENDIENTE".equals(c.getEstado())
                        || "RECHAZADO".equals(c.getEstado()))
                .count();
        if (conciliacionesRojas > 0) {
            throw new NegocioException(
                    "No se puede aprobar: hay " + conciliacionesRojas
                            + " conciliación(es) AIRHSP sin resolver");
        }

        // Gate 3 — Sin movimientos en NETO_NO_VA (REGLA SERVIR-07 / §5.4).
        long netosObservados = movimientoRepository
                .findByPeriodoAndActivo(entity.getPeriodo(), 1)
                .stream()
                .filter(m -> "NETO_NO_VA".equals(m.getEstadoNeto()))
                .count();
        if (netosObservados > 0) {
            throw new NegocioException(
                    "No se puede aprobar: hay " + netosObservados
                            + " movimiento(s) con neto observado (NETO_NO_VA)");
        }

        entity.setNroCertPresup(dto.getNroCertPresup().trim());
        entity.setFechaAprobacion(LocalDateTime.now());
        entity.setEstado(APROBADO);
        repository.save(entity);

        auditoriaContext.setDetalle(
                "Periodo " + id + " aprobado — cert. presupuestal "
                        + entity.getNroCertPresup());
    }

    // ============================
    // CERRAR  (APROBADO → CERRADO)
    // ============================

    @Auditable(accion = "CERRAR_PERIODO_PLANILLA")
    public void cerrar(Long id) {
        PeriodoPlanilla entity = obtener(id);
        if (!APROBADO.equals(entity.getEstado())) {
            throw new NegocioException(
                    "Solo un período APROBADO puede cerrarse "
                            + "(estado actual: " + entity.getEstado() + ")");
        }
        entity.setEstado(CERRADO);
        entity.setFechaCierre(LocalDateTime.now());
        repository.save(entity);
        auditoriaContext.setDetalle("Periodo cerrado ID: " + id);
    }

    // ============================
    // REABRIR  (retrocede un paso en el flujo)
    // ============================

    @Auditable(accion = "REABRIR_PERIODO_PLANILLA")
    public void reabrir(Long id) {
        PeriodoPlanilla entity = obtener(id);

        switch (entity.getEstado() == null ? "" : entity.getEstado()) {
            case CERRADO -> {
                entity.setEstado(APROBADO);
                entity.setFechaCierre(null);
            }
            case APROBADO -> {
                entity.setEstado(EN_REVISION);
                entity.setFechaAprobacion(null);
            }
            case EN_REVISION -> entity.setEstado(ABIERTO);
            default -> throw new NegocioException(
                    "El período ya está ABIERTO — no se puede retroceder más");
        }

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Periodo " + id + " retrocedido a " + entity.getEstado());
    }

    // ============================
    // ELIMINAR
    // ============================

    @Auditable(accion = "ELIMINAR_PERIODO_PLANILLA")
    public void eliminar(Long id) {
        PeriodoPlanilla entity = obtener(id);
        entity.setActivo(0);
        repository.save(entity);
        auditoriaContext.setDetalle("Periodo eliminado ID: " + id);
    }

    // ============================
    // HELPER
    // ============================

    private PeriodoPlanilla obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NegocioException("Periodo no encontrado"));
    }
}
