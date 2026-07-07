package com.indeci.rrhh.service.cts;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.cts.CtsCandidatoDto;
import com.indeci.rrhh.dto.cts.CtsDesgloseDto;
import com.indeci.rrhh.dto.cts.CtsLiquidacionResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.LiquidacionCts;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.LiquidacionCtsRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Feature 016 — Orquestación de la Liquidación de CTS Trunca: listado de
 * candidatos, desglose de trazabilidad, aprobación (sello inmutable) y constancia.
 */
@Service
@RequiredArgsConstructor
public class CtsLiquidacionService {

    private static final String SIN_FECHA_CESE = "SIN_FECHA_CESE";

    private final EmpleadoPlanillaRepository planillaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final LiquidacionCtsRepository liquidacionRepository;

    /**
     * Cesantes del régimen aptos (o no) para liquidación trunca en el período.
     * Un vínculo activo con cese registrado es apto; sin fechaCese se marca con
     * bloqueo para el Poka-Yoke visual del buscador.
     */
    @Transactional(readOnly = true)
    public List<CtsCandidatoDto> listarCandidatos(String periodo, Long regimenLaboralId) {
        String regimenCodigo = regimenLaboralRepository.findById(regimenLaboralId)
                .map(RegimenLaboral::getCodigo).orElse(null);

        List<CtsCandidatoDto> out = new ArrayList<>();
        for (EmpleadoPlanilla v : planillaRepository.findByRegimenLaboralIdAndActivo(regimenLaboralId, 1)) {
            boolean cesado = v.getFechaCese() != null
                    || "CESADO".equalsIgnoreCase(v.getEstadoLaboral());
            if (!cesado) {
                continue; // solo personal cesado entra a la liquidación trunca
            }
            boolean apto = v.getFechaCese() != null;
            String[] persona = resolverPersona(v.getEmpleadoId());
            out.add(new CtsCandidatoDto(
                    v.getEmpleadoId(), v.getId(), persona[0], persona[1],
                    regimenCodigo, v.getFechaCese(), v.getMotivoCese(),
                    apto ? "PENDIENTE" : "BLOQUEADO",
                    apto, apto ? null : SIN_FECHA_CESE));
        }
        return out;
    }

    /** Desglose de trazabilidad de una liquidación calculada (panel drawer). */
    @Transactional(readOnly = true)
    public CtsDesgloseDto obtenerDesglose(Long liquidacionId) {
        LiquidacionCts l = obtener(liquidacionId);
        String[] persona = resolverPersona(l.getEmpleadoId());

        List<CtsDesgloseDto.ConceptoExcluido> excluidos = List.of(
                new CtsDesgloseDto.ConceptoExcluido(
                        "Valorización Ajustada / Priorizada (excluida por Ley 30057)", BigDecimal.ZERO),
                new CtsDesgloseDto.ConceptoExcluido(
                        "Aguinaldos / Conceptos extraordinarios (excluidos)", BigDecimal.ZERO));

        String formula = "(" + l.getAnios() + " x " + l.getBaseComputable().toPlainString()
                + " x " + l.getFactorAnual().toPlainString() + ") + (("
                + l.getBaseComputable().toPlainString() + " x " + l.getFactorAnual().toPlainString()
                + " / " + l.getDivisorDias() + ") x " + l.getDiasEfectivosFraccion() + ")";

        return new CtsDesgloseDto(
                l.getId(), persona[0], persona[1], l.getRegimenCodigo(), l.getEstrategia(),
                l.getFechaIngreso(), l.getFechaCese(), l.getAnios(), l.getMeses(), l.getDias(),
                l.getDiasEfectivosFraccion(), l.getBaseComputable(), l.getFactorAnual(),
                l.getDivisorDias(), excluidos, l.getMontoAnios(), l.getMontoFraccion(),
                l.getMontoTotal(), formula, marcoNormativo(l.getRegimenCodigo()), l.getEstado());
    }

    /** Aprueba y sella la liquidación: CALCULADO → CERRADO (inmutable). */
    @Transactional
    @Auditable(accion = "CTS_APROBAR")
    public CtsLiquidacionResponseDto aprobar(Long liquidacionId) {
        LiquidacionCts l = obtener(liquidacionId);
        if ("CERRADO".equalsIgnoreCase(l.getEstado())) {
            throw new NegocioException("La liquidación de CTS " + liquidacionId + " ya está CERRADA.");
        }
        if (!"CALCULADO".equalsIgnoreCase(l.getEstado())) {
            throw new NegocioException(
                    "Solo se puede aprobar una liquidación en estado CALCULADO (actual: " + l.getEstado() + ").");
        }
        l.setEstado("CERRADO");
        l.setUpdatedAt(LocalDateTime.now());
        l = liquidacionRepository.save(l);
        return new CtsLiquidacionResponseDto(
                l.getId(), l.getEmpleadoId(), l.getEmpleadoPlanillaId(), l.getPeriodo(),
                l.getRegimenCodigo(), l.getEstrategia(), l.getFechaIngreso(), l.getFechaCese(),
                l.getAnios(), l.getMeses(), l.getDias(), l.getBaseComputable(),
                l.getMontoAnios(), l.getMontoFraccion(), l.getMontoTotal(), l.getEstado());
    }

    // ======================================================================

    private LiquidacionCts obtener(Long id) {
        return liquidacionRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Liquidación de CTS no existe: " + id));
    }

    /** Devuelve [dni, nombre] del empleado (best-effort, sin romper si falta). */
    private String[] resolverPersona(Long empleadoId) {
        if (empleadoId == null) {
            return new String[]{null, null};
        }
        return empleadoRepository.findById(empleadoId)
                .map(Empleado::getPersona)
                .map(p -> new String[]{p.getDni(), p.getNombreCompleto()})
                .orElse(new String[]{null, null});
    }

    private String marcoNormativo(String regimenCodigo) {
        if (regimenCodigo != null && ("SERVIR".equalsIgnoreCase(regimenCodigo)
                || "30057".equals(regimenCodigo))) {
            return "Ley N.° 30057 Art. 34 + D.S. 040-2014-PCM — 100% de la Valorización "
                    + "Principal por año de servicios; excluye ajustada/priorizada, "
                    + "aguinaldos y extraordinarios. No aplican depósitos semestrales.";
        }
        return "D.Leg. 276 — Compensación por Tiempo de Servicios sobre la remuneración "
                + "principal (MUC) al cese; excluye aguinaldos y fracciones extraordinarias.";
    }
}
