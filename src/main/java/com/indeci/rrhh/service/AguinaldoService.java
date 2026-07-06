package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AguinaldoRequest;
import com.indeci.rrhh.dto.AguinaldoResultDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.TipoProceso;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.PlanillaLoteRepository;
import com.indeci.rrhh.entity.PlanillaLote;

import lombok.RequiredArgsConstructor;

/**
 * Track B — Generación del AGUINALDO como proceso APARTE (tipo de planilla
 * {@code AGUINALDO}), no dentro de la planilla regular. Reglas por régimen en
 * {@link AguinaldoCalculator} (SERVIR 100% / CAS %manual con piso / 276 fijo).
 *
 * <h3>Requisito A — orden de proceso (renta 5ta del propio mes)</h3>
 * El aguinaldo debe generarse ANTES de la planilla regular del mismo período,
 * para que la retención de 5ta de julio/diciembre ya lo incluya vía
 * {@code acumularHistorico5ta} (que suma los ingresos de los movimientos activos
 * del año). Este servicio NO recalcula ni bloquea la planilla regular (CONSTRAINT
 * #7): si al generar el aguinaldo ya existe planilla regular del período, se
 * devuelve una <b>advertencia</b> (no bloqueo) indicando que la retención de 5ta
 * se ajustará en el mes siguiente.
 *
 * <h3>Requisito B — descuento judicial obligatorio</h3>
 * La inafectación del aguinaldo NO alcanza a las retenciones judiciales por
 * alimentos ("descuentos establecidos por ley"). Si el trabajador tiene retención
 * judicial activa con porcentaje, el movimiento AGUINALDO incluye ese descuento
 * calculado sobre el monto del aguinaldo (mismo % del mandato), reutilizando el
 * concepto judicial existente ({@code DESCUENTO_JUDICIAL}) sin tocar la lógica
 * regular. Los mandatos judiciales de monto fijo (sin %) quedan como punto de
 * extensión (no se aplican automáticamente sobre el aguinaldo).
 *
 * <h3>Afectación (requisito #3)</h3>
 * El movimiento AGUINALDO es solo ingreso: no se le computan AFP/ONP/EsSalud
 * (no se corre el motor regular). Su afectación a renta 5ta ocurre por el
 * histórico de la planilla regular siguiente (los conceptos de aguinaldo se
 * siembran {@code AFECTO_IR_5TA='S'}).
 */
@Service
@RequiredArgsConstructor
public class AguinaldoService {

    private static final String TIPO_PLANILLA_AGUINALDO = "AGUINALDO";
    private static final String COD_JUDICIAL = "DESCUENTO_JUDICIAL";
    private static final BigDecimal CIEN = new BigDecimal("100");

    /** Estados elegibles al corte (requisito #4). */
    private static final Set<String> ESTADOS_ELEGIBLES =
            Set.of("ACTIVO", "VACACIONES", "LICENCIA_CON_GOCE", "SUBSIDIO");

    private final EmpleadoPlanillaRepository planillaRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository movimientoPlanillaDetalleRepository;
    private final PlanillaLoteRepository planillaLoteRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final EmpleadoConceptoRepository empleadoConceptoRepository;
    private final ParametroRemunerativoService parametroService;
    private final GeneradorPlanillaService motor;

    @Transactional
    public AguinaldoResultDto generar(AguinaldoRequest req) {
        String periodo = req.getPeriodo();
        LocalDate fp = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        int anio = fp.getYear();
        int mes = fp.getMonthValue();
        if (mes != 7 && mes != 12) {
            throw new NegocioException(
                    "El aguinaldo solo se genera en julio o diciembre. Período: " + periodo);
        }

        LocalDate corte = req.getFechaCorte();
        BigDecimal pctCas = req.getPctCas();
        BigDecimal pisoCas = parametroService
                .obtenerValorOpcional("AGUINALDO_CAS_PISO", anio, null).orElse(BigDecimal.ZERO);
        BigDecimal monto276 = parametroService
                .obtenerValorOpcional("AGUINALDO_276_MONTO", anio, null).orElse(BigDecimal.ZERO);

        List<String> advertencias = new ArrayList<>();
        // (A) Orden de proceso: si ya hay planilla regular del período, advertir (no bloquear).
        boolean regularExiste = movimientoRepository.findByPeriodoAndActivo(periodo, 1).stream()
                .anyMatch(m -> TipoProceso.fromTipoPlanilla(m.getTipoPlanilla()) == TipoProceso.REGULAR);
        if (regularExiste) {
            advertencias.add("Ya existe planilla regular del período " + periodo
                    + ": la retención de 5ta del aguinaldo se ajustará en el mes siguiente "
                    + "(no se recalcula la planilla regular ya generada).");
        }

        List<AguinaldoResultDto.Excluido> excluidos = new ArrayList<>();
        int generados = 0;

        java.util.Map<String, Long> loteIdsPorRegimen = new java.util.HashMap<>();

        for (EmpleadoPlanilla v : planillaRepository.findByActivo(1)) {
            if (req.getRegimenLaboralId() != null
                    && !req.getRegimenLaboralId().equals(v.getRegimenLaboralId())) {
                continue; // fuera del filtro de régimen
            }
            String motivo = motivoInelegible(v, corte, fp);
            if (motivo != null) {
                excluidos.add(new AguinaldoResultDto.Excluido(v.getEmpleadoId(), motivo));
                continue;
            }

            String regimen = v.getRegimenLaboralId() != null
                    ? motor.resolverRegimenLaboralCodigo(v.getRegimenLaboralId()) : null;
            BigDecimal base = motor.resolverBaseRemunerativa(v, periodo);
            BigDecimal monto = AguinaldoCalculator.calcular(regimen, base, pctCas, monto276, pisoCas);
            if (monto.signum() <= 0) {
                excluidos.add(new AguinaldoResultDto.Excluido(v.getEmpleadoId(),
                        "Régimen sin aguinaldo por esta vía: " + regimen));
                continue;
            }

            String codMef = codigoMefAguinaldo(regimen, mes);
            if (codMef == null) {
                excluidos.add(new AguinaldoResultDto.Excluido(v.getEmpleadoId(),
                        "Sin concepto de aguinaldo para régimen " + regimen));
                continue;
            }

            // (B) Descuento judicial obligatorio (mismo % del mandato) sobre el aguinaldo.
            EmpleadoConcepto judicial = judicialActivo(v.getEmpleadoId());
            BigDecimal descJudicial = BigDecimal.ZERO;
            if (judicial != null && judicial.getPorcentaje() != null) {
                descJudicial = monto.multiply(BigDecimal.valueOf(judicial.getPorcentaje()))
                        .divide(CIEN, 2, RoundingMode.HALF_UP);
            }
            BigDecimal neto = monto.subtract(descJudicial);

            // Resolver Lote ID (Upsert por régimen)
            Long loteId = loteIdsPorRegimen.computeIfAbsent(regimen, r -> {
                PlanillaLote lote = planillaLoteRepository
                        .findByPeriodoAndRegimenLaboralCodigoAndTipoPlanillaAndCorrelativo(periodo, r, TIPO_PLANILLA_AGUINALDO, 1)
                        .orElse(null);

                if (lote == null) {
                    lote = new PlanillaLote();
                    lote.setPeriodo(periodo);
                    lote.setRegimenLaboralCodigo(r);
                    lote.setTipoPlanilla(TIPO_PLANILLA_AGUINALDO);
                    lote.setConceptoPlanilla("AGUINALDO");
                    lote.setCorrelativo(1);
                    lote.setEstado("GENERADO");
                    lote.setCreadoPor("SISTEMA"); // Track B - Aguinaldo Service
                    lote.setCreadoEn(LocalDateTime.now());
                    lote = planillaLoteRepository.save(lote);
                } else {
                    // Upsert: Borrar movimientos anteriores del lote
                    List<MovimientoPlanilla> movs = movimientoRepository.findByLoteId(lote.getId());
                    for (MovimientoPlanilla m : movs) {
                        movimientoPlanillaDetalleRepository.deleteByMovimientoPlanillaId(m.getId());
                    }
                    movimientoRepository.deleteAll(movs);
                    // Actualizar fecha
                    lote.setCreadoEn(LocalDateTime.now());
                    lote = planillaLoteRepository.save(lote);
                }
                return lote.getId();
            });

            // Movimiento tipo AGUINALDO: solo ingreso (no se corre el motor regular → #7).
            MovimientoPlanilla mov = new MovimientoPlanilla();
            mov.setEmpleadoId(v.getEmpleadoId());
            mov.setPeriodo(periodo);
            mov.setTipoPlanilla(TIPO_PLANILLA_AGUINALDO);
            mov.setLoteId(loteId);
            mov.setTotalIngresos(monto.doubleValue());
            mov.setTotalDescuentos(descJudicial.doubleValue());
            mov.setNetoPagar(neto.doubleValue());
            mov.setEstado("GENERADO");
            mov.setActivo(1);
            mov.setCreatedAt(LocalDateTime.now());
            mov.setRegimenLaboralSnapshot(regimen);
            MovimientoPlanilla saved = movimientoRepository.save(mov);

            motor.grabarDetalle(saved.getId(), motor.conceptoPorCodigoMef(codMef), monto,
                    "Aguinaldo " + regimen + " (" + (mes == 7 ? "Fiestas Patrias" : "Navidad") + ")");
            if (descJudicial.signum() > 0) {
                ConceptoPlanilla cj = conceptoRepository
                        .findById(judicial.getConceptoPlanillaId()).orElse(null);
                if (cj != null) {
                    motor.grabarDetalle(saved.getId(), cj, descJudicial,
                            "Retención judicial " + judicial.getPorcentaje() + "% sobre aguinaldo");
                }
            }
            generados++;
        }

        return new AguinaldoResultDto(generados, excluidos, advertencias);
    }

    /** Requisito #4 — elegibilidad al corte. {@code null} = elegible; texto = motivo. */
    private String motivoInelegible(EmpleadoPlanilla v, LocalDate corte, LocalDate fp) {
        LocalDate inicio = v.getFechaInicioContrato() != null
                ? v.getFechaInicioContrato() : v.getFechaInicio();
                
        // Regla: No generar si cesó antes del inicio del período (ej. cesó en junio, no recibe aguinaldo en julio)
        if (v.getFechaCese() != null && v.getFechaCese().isBefore(fp)) {
            return "Cesado antes del inicio del período (" + v.getFechaCese() + ")";
        }

        if (corte != null) {
            if (inicio != null && inicio.isAfter(corte)) {
                return "Vínculo inicia después de la fecha de corte";
            }
            if (v.getFechaCese() != null && v.getFechaCese().isBefore(corte)) {
                return "Cesado antes de la fecha de corte (" + v.getFechaCese() + ")";
            }
        }
        String estado = v.getEstadoLaboral();
        if (estado != null && !ESTADOS_ELEGIBLES.contains(estado.trim().toUpperCase())) {
            return "Estado no elegible: " + estado;
        }
        return null;
    }

    /** Concepto judicial activo del trabajador (para el descuento sobre el aguinaldo). */
    private EmpleadoConcepto judicialActivo(Long empleadoId) {
        for (EmpleadoConcepto ec : empleadoConceptoRepository.findByEmpleadoIdAndActivo(empleadoId, 1)) {
            ConceptoPlanilla c = ec.getConceptoPlanillaId() != null
                    ? conceptoRepository.findById(ec.getConceptoPlanillaId()).orElse(null) : null;
            if (c != null && esJudicial(c)) {
                return ec;
            }
        }
        return null;
    }

    private boolean esJudicial(ConceptoPlanilla c) {
        return COD_JUDICIAL.equalsIgnoreCase(c.getCodigo())
                || COD_JUDICIAL.equalsIgnoreCase(c.getTipoConcepto());
    }

    /** Concepto MEF del aguinaldo por régimen y mes (jul/dic). {@code null} si no aplica. */
    private String codigoMefAguinaldo(String regimen, int mes) {
        if (regimen == null) {
            return null;
        }
        String r = regimen.trim().toUpperCase();
        if ("276".equals(r)) {
            return mes == 7 ? "00201" : "00202";
        }
        if ("CAS".equals(r) || "1057".equals(r)) {
            return mes == 7 ? "0077" : "0025";
        }
        if ("SERVIR".equals(r)) {
            return "AGUISRVPV"; // provisional PENDIENTE_VALIDACION (código MEF por RR.HH.)
        }
        return null;
    }
}
