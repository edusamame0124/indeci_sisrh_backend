package com.indeci.rrhh.service.cts;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.cts.CtsLiquidacionResponseDto;
import com.indeci.rrhh.entity.CalculoSnapshot;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.LiquidacionCts;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.LiquidacionCtsRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.service.CalculoSnapshotService;
import com.indeci.rrhh.service.ParametroRemunerativoService;
import com.indeci.rrhh.service.cts.CtsTiempoServiciosCalculator.TiempoServicios;
import com.indeci.rrhh.service.cts.strategy.CtsStrategy;
import com.indeci.rrhh.service.cts.strategy.CtsStrategyFactory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Feature 016 — Núcleo de cálculo de la Liquidación de CTS Trunca.
 *
 * <p>Orden invariable: <b>guard CAS → fechaCese obligatoria → estrategia por
 * régimen → base &gt; 0 → tiempo de servicios → monto → snapshot → persistir</b>.
 * Todos los factores/divisor vienen de parámetro (REGLA-02): cero magic numbers.</p>
 */
@Service
@RequiredArgsConstructor
public class CtsCalculadorService {

    private static final String PARAM_DIVISOR = "CTS_DIVISOR_DIAS";
    private static final int ESCALA_INTERNA = 10;

    private final EmpleadoPlanillaRepository planillaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final LiquidacionCtsRepository liquidacionRepository;
    private final ParametroRemunerativoService parametroService;
    private final CalculoSnapshotService snapshotService;
    private final CtsStrategyFactory strategyFactory;
    private final CtsCasGuard casGuard;
    private final CtsTiempoServiciosCalculator tiempoCalculator;

    @Transactional
    public CtsLiquidacionResponseDto calcular(Long empleadoId, Long empleadoPlanillaId, String periodo) {
        EmpleadoPlanilla vinculo = planillaRepository.findById(empleadoPlanillaId)
                .orElseThrow(() -> new NegocioException(
                        "Vínculo (configuración remunerativa) no existe: " + empleadoPlanillaId));

        if (vinculo.getEmpleadoId() == null || !vinculo.getEmpleadoId().equals(empleadoId)) {
            throw new NegocioException(
                    "El vínculo " + empleadoPlanillaId + " no pertenece al empleado " + empleadoId);
        }

        String regimenCodigo = resolverRegimenCodigo(vinculo.getRegimenLaboralId());

        // 1) Poka-Yoke CAS (D.Leg. 1057 no tiene CTS).
        casGuard.verificar(regimenCodigo);

        // 2) Candado: fecha de cese obligatoria (fuente única = el vínculo).
        if (vinculo.getFechaCese() == null) {
            throw new NegocioException(
                    "No se puede generar liquidación de CTS: El servidor no registra una "
                            + "fecha de cese oficial en su configuración remunerativa");
        }

        // 2b) Inmutabilidad: un vínculo/período ya CERRADO no se recalcula.
        liquidacionRepository.findByEmpleadoPlanillaIdAndPeriodo(empleadoPlanillaId, periodo)
                .filter(l -> "CERRADO".equalsIgnoreCase(l.getEstado()))
                .ifPresent(l -> {
                    throw new NegocioException(
                            "La liquidación de CTS del vínculo " + empleadoPlanillaId
                                    + " en el período " + periodo + " está CERRADA (inmutable).");
                });

        int anioFiscal = anioDePeriodo(periodo);

        // 3) Estrategia por régimen (276 | SERVIR) — factor parametrizado.
        CtsStrategy estrategia = strategyFactory.resolver(regimenCodigo);
        BigDecimal base = estrategia.resolverBaseComputable(vinculo, anioFiscal);
        if (base == null || base.signum() <= 0) {
            throw new NegocioException(
                    "No se puede liquidar CTS: la base computable (VP/MUC) del vínculo "
                            + empleadoPlanillaId + " no está registrada o es cero.");
        }
        BigDecimal factor = estrategia.resolverFactorAnual(anioFiscal, vinculo.getRegimenLaboralId());
        BigDecimal divisor = parametroService.obtenerValor(PARAM_DIVISOR, anioFiscal, null);

        // 4) Tiempo de servicios computable (del vínculo exacto).
        LocalDate ingreso = resolverIngreso(vinculo);
        TiempoServicios t = tiempoCalculator.computar(ingreso, vinculo.getFechaCese());

        // 5) Monto: (años × base × factor) + ((base × factor / divisor) × díasFracción).
        BigDecimal baseFactor = base.multiply(factor);
        BigDecimal montoAnios = baseFactor
                .multiply(BigDecimal.valueOf(t.anios()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoFraccion = baseFactor
                .divide(divisor, ESCALA_INTERNA, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(t.diasFraccion()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = montoAnios.add(montoFraccion);

        // 6) Snapshot de trazabilidad (REGLA_CTS — requiere V012_15 en el CK).
        snapshotService.desactivarPrevios(empleadoId, periodo);
        CalculoSnapshot snap = snapshotService.registrar(
                CalculoSnapshotService.registro(empleadoId, periodo, CalculoSnapshotService.REGLA_CTS)
                        .base(base)
                        .resultado(total)
                        .version(String.valueOf(anioFiscal))
                        .formula("(" + t.anios() + " x " + base.toPlainString() + " x " + factor.toPlainString()
                                + ") + ((" + base.toPlainString() + " x " + factor.toPlainString()
                                + " / " + divisor.toPlainString() + ") x " + t.diasFraccion() + ")")
                        .param("estrategia", estrategia.estrategiaCodigo())
                        .param("regimen", regimenCodigo)
                        .param("empleadoPlanillaId", empleadoPlanillaId)
                        .param("baseComputable", base)
                        .param("factorAnual", factor)
                        .param("divisorDias", divisor)
                        .param("anios", t.anios())
                        .param("meses", t.meses())
                        .param("dias", t.dias())
                        .param("diasFraccion", t.diasFraccion())
                        .param("montoAnios", montoAnios)
                        .param("montoFraccion", montoFraccion)
                        .param("fechaIngreso", ingreso)
                        .param("fechaCese", vinculo.getFechaCese()));

        // 7) Persistir (upsert por UK vínculo/período; recalculable si no CERRADO).
        LiquidacionCts liq = liquidacionRepository
                .findByEmpleadoPlanillaIdAndPeriodo(empleadoPlanillaId, periodo)
                .orElseGet(LiquidacionCts::new);
        boolean nuevo = liq.getId() == null;

        liq.setEmpleadoId(empleadoId);
        liq.setEmpleadoPlanillaId(empleadoPlanillaId);
        liq.setPeriodo(periodo);
        liq.setRegimenLaboralId(vinculo.getRegimenLaboralId());
        liq.setRegimenCodigo(regimenCodigo);
        liq.setEstrategia(estrategia.estrategiaCodigo());
        liq.setFechaIngreso(ingreso);
        liq.setFechaCese(vinculo.getFechaCese());
        liq.setAnios(t.anios());
        liq.setMeses(t.meses());
        liq.setDias(t.dias());
        liq.setDiasEfectivosFraccion((int) t.diasFraccion());
        liq.setBaseComputable(base);
        liq.setFactorAnual(factor);
        liq.setDivisorDias(divisor.intValue());
        liq.setMontoAnios(montoAnios);
        liq.setMontoFraccion(montoFraccion);
        liq.setMontoTotal(total);
        liq.setEstado("CALCULADO");
        liq.setSnapshotId(snap != null ? snap.getId() : null);
        if (nuevo) {
            liq.setCreatedAt(LocalDateTime.now());
        } else {
            liq.setUpdatedAt(LocalDateTime.now());
        }
        liq = liquidacionRepository.save(liq);

        return toResponse(liq);
    }

    // ======================================================================

    private String resolverRegimenCodigo(Long regimenLaboralId) {
        if (regimenLaboralId == null) {
            return null;
        }
        return regimenLaboralRepository.findById(regimenLaboralId)
                .map(r -> r.getCodigo())
                .orElse(null);
    }

    private LocalDate resolverIngreso(EmpleadoPlanilla v) {
        if (v.getFechaInicioContrato() != null) return v.getFechaInicioContrato();
        if (v.getFechaIngreso() != null) return v.getFechaIngreso();
        return v.getFechaInicio();
    }

    private int anioDePeriodo(String periodo) {
        if (periodo == null) {
            throw new NegocioException("Período inválido (esperado YYYY-MM o YYYYMM): null");
        }
        String p = periodo.replace("-", "").trim();
        if (p.length() < 4) {
            throw new NegocioException("Período inválido: " + periodo);
        }
        return Integer.parseInt(p.substring(0, 4));
    }

    private CtsLiquidacionResponseDto toResponse(LiquidacionCts l) {
        return new CtsLiquidacionResponseDto(
                l.getId(), l.getEmpleadoId(), l.getEmpleadoPlanillaId(), l.getPeriodo(),
                l.getRegimenCodigo(), l.getEstrategia(), l.getFechaIngreso(), l.getFechaCese(),
                l.getAnios(), l.getMeses(), l.getDias(), l.getBaseComputable(),
                l.getMontoAnios(), l.getMontoFraccion(), l.getMontoTotal(), l.getEstado());
    }
}
