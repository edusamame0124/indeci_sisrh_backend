package com.indeci.rrhh.service.subsidio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioLiquidacion;
import com.indeci.rrhh.entity.SubsidioLiquidacionMovimiento;
import com.indeci.rrhh.entity.SubsidioReglaConcepto;
import com.indeci.rrhh.entity.SubsidioReglaVigencia;
import com.indeci.rrhh.entity.SubsidioTramo;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.repository.SubsidioLiquidacionMovimientoRepository;
import com.indeci.rrhh.repository.SubsidioLiquidacionRepository;
import com.indeci.rrhh.repository.SubsidioReglaConceptoRepository;
import com.indeci.rrhh.repository.SubsidioTramoRepository;
import com.indeci.rrhh.subsidio.SubsidioEstados;
import com.indeci.rrhh.subsidio.SubsidioPeriodoUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubsidioPlanillaIntegracionService {

    private final SubsidioLiquidacionRepository liquidacionRepository;
    private final SubsidioLiquidacionMovimientoRepository movimientoSubsidioRepository;
    private final SubsidioTramoRepository tramoRepository;
    private final SubsidioCasoRepository casoRepository;
    private final SubsidioReglaConceptoRepository reglaConceptoRepository;
    private final SubsidioReglaResolverService reglaResolver;
    private final SubsidioValidacionService validacionService;
    private final SubsidioTimelineService timelineService;
    private final MovimientoPlanillaDetalleRepository detalleRepository;
    private final ConceptoPlanillaRepository conceptoRepository;

    @Transactional
    public SubsidioLiquidacion aplicarPlanilla(Long liquidacionId) {
        SubsidioLiquidacion liq = liquidacionRepository.findById(liquidacionId)
                .orElseThrow(() -> new NegocioException("Liquidación no encontrada"));
        validacionService.assertLiquidacionNoAplicada(liq);
        if (!SubsidioEstados.LIQ_CALCULADO.equals(liq.getEstado())) {
            throw new NegocioException("Solo se pueden aplicar liquidaciones en estado CALCULADO");
        }

        SubsidioTramo tramo = tramoRepository.findById(liq.getTramoId()).orElseThrow();
        SubsidioCaso caso = casoRepository.findById(tramo.getCasoId()).orElseThrow();
        String periodoPlanilla = SubsidioPeriodoUtil.aPlanilla(tramo.getPeriodo());
        validacionService.assertPeriodoAbierto(periodoPlanilla);

        // F1.10 — El MOTOR es la fuente ÚNICA de las líneas de subsidio en la
        // boleta: las graba durante la generación ordinaria
        // (grabarSubsidioEnMovimientoMotor), tras borrar los detalles previos,
        // garantizando línea == total de forma determinista y sin líneas
        // huérfanas ni doble conteo. aplicarPlanilla ya NO inserta líneas en el
        // movimiento; solo marca la liquidación como APLICADA y persiste los días
        // de subsidio para el prorrateo 1/30 del haber (F1.8).
        liq.setDiasSubsidio(tramo.getDiasSubsidio());
        liq.setEstado(SubsidioEstados.LIQ_APLICADO_PLANILLA);
        liquidacionRepository.save(liq);

        tramo.setEstadoTramo(SubsidioEstados.TRAMO_APLICADO);
        tramoRepository.save(tramo);

        caso.setEstado(SubsidioEstados.CASO_APLICADO_PLANILLA);
        casoRepository.save(caso);

        timelineService.registrar(caso.getId(), "APLICACION_PLANILLA",
                "Liquidación aplicada al periodo " + periodoPlanilla, liq.getId());
        return liq;
    }

    @Transactional(readOnly = true)
    public BigDecimal ingresoSubsidioMotor(Long empleadoId, String periodoPlanilla) {
        String periodoSubsidio = SubsidioPeriodoUtil.aSubsidio(periodoPlanilla);
        List<SubsidioLiquidacion> liquidaciones = liquidacionRepository
                .findVigentesPorEmpleadoPeriodoEstado(
                        empleadoId, periodoSubsidio, SubsidioEstados.LIQ_APLICADO_PLANILLA);
        return liquidaciones.stream()
                .map(SubsidioLiquidacion::getContraprestacionEquivalente)
                .filter(m -> m != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * F1.10 — Fuente ÚNICA de las líneas de subsidio en la boleta. El motor la
     * invoca durante la generación ordinaria (después de {@code
     * borrarMovimientoAnterior}) para grabar las líneas de detalle
     * (subsidio 100% + diferencial INDECI) de las liquidaciones APLICADAS en el
     * movimiento recién regenerado, devolviendo su total. Así la boleta muestra
     * siempre las líneas y el total almacenado de forma consistente
     * (línea == total), sin líneas huérfanas ni doble conteo.
     *
     * @return total del subsidio grabado (a sumar a {@code totalIngresos}). 0 si no hay.
     */
    @Transactional
    public BigDecimal grabarSubsidioEnMovimientoMotor(
            Long empleadoId, String periodoPlanilla, Long movimientoId) {
        String periodoSubsidio = SubsidioPeriodoUtil.aSubsidio(periodoPlanilla);
        List<SubsidioLiquidacion> liquidaciones = liquidacionRepository
                .findVigentesPorEmpleadoPeriodoEstado(
                        empleadoId, periodoSubsidio, SubsidioEstados.LIQ_APLICADO_PLANILLA);
        BigDecimal total = BigDecimal.ZERO;
        for (SubsidioLiquidacion liq : liquidaciones) {
            total = total.add(grabarLineasSubsidio(liq, movimientoId));
        }
        return total;
    }

    /** F1.10 — Graba las líneas (subsidio 100% + diferencial) de una liquidación en el movimiento y devuelve su total. */
    private BigDecimal grabarLineasSubsidio(SubsidioLiquidacion liq, Long movimientoId) {
        SubsidioTramo tramo = tramoRepository.findById(liq.getTramoId()).orElseThrow();
        SubsidioCaso caso = casoRepository.findById(tramo.getCasoId()).orElseThrow();
        SubsidioReglaVigencia regla = reglaResolver.resolverVigente(tramo.getFechaDesde());
        String tipoConcepto = mapTipoSubsidio(caso.getTipoCaso());
        BigDecimal total = BigDecimal.ZERO;

        if (liq.getContraprestacionEquivalente() != null
                && liq.getContraprestacionEquivalente().signum() != 0) {
            ConceptoPlanilla conceptoSubsidio = resolverConcepto(
                    regla.getId(), tipoConcepto, SubsidioEstados.IMPUT_SUBSIDIO_100);
            grabarDetalle(movimientoId, conceptoSubsidio,
                    liq.getContraprestacionEquivalente(),
                    "Subsidio 100% tramo " + tramo.getPeriodo());
            total = total.add(liq.getContraprestacionEquivalente());
        }

        if (liq.getDiferencialIndeci() != null && liq.getDiferencialIndeci().signum() != 0) {
            ConceptoPlanilla conceptoDiferencial = resolverConcepto(
                    regla.getId(), "DIFERENCIAL", SubsidioEstados.IMPUT_DIFERENCIAL_2073);
            grabarDetalle(movimientoId, conceptoDiferencial,
                    liq.getDiferencialIndeci(),
                    "Diferencial INDECI tramo " + tramo.getPeriodo());
            total = total.add(liq.getDiferencialIndeci());
        }
        return total;
    }

    /**
     * F1.8 — Contrato de dominio: días de subsidio aplicados a la planilla del
     * período. El motor los consume para reducir el haber ordinario (divisor
     * 1/30) sin conocer las tablas internas del módulo de subsidios.
     *
     * @return suma de días de subsidio de las liquidaciones aplicadas, con tope
     *         defensivo de 30 (un mes ordinario nunca excede 30 días). 0 si no hay.
     */
    @Transactional(readOnly = true)
    public int diasSubsidioMotor(Long empleadoId, String periodoPlanilla) {
        String periodoSubsidio = SubsidioPeriodoUtil.aSubsidio(periodoPlanilla);
        List<SubsidioLiquidacion> liquidaciones = liquidacionRepository
                .findVigentesPorEmpleadoPeriodoEstado(
                        empleadoId, periodoSubsidio, SubsidioEstados.LIQ_APLICADO_PLANILLA);
        int dias = liquidaciones.stream()
                .map(SubsidioLiquidacion::getDiasSubsidio)
                .filter(d -> d != null)
                .mapToInt(Integer::intValue)
                .sum();
        return Math.min(dias, 30);
    }

    @Transactional
    public SubsidioLiquidacion revertir(Long liquidacionId, String motivo) {
        SubsidioLiquidacion liq = liquidacionRepository.findById(liquidacionId)
                .orElseThrow(() -> new NegocioException("Liquidación no encontrada"));
        if (!SubsidioEstados.LIQ_APLICADO_PLANILLA.equals(liq.getEstado())) {
            throw new NegocioException("Solo se pueden revertir liquidaciones aplicadas a planilla");
        }
        List<SubsidioLiquidacionMovimiento> links = movimientoSubsidioRepository.findByLiquidacionId(liquidacionId);
        for (SubsidioLiquidacionMovimiento link : links) {
            if (link.getMovimientoDetId() != null) {
                detalleRepository.deleteById(link.getMovimientoDetId());
            }
            link.setEstado("REVERTIDO");
            movimientoSubsidioRepository.save(link);
        }
        liq.setEstado(SubsidioEstados.LIQ_CALCULADO);
        liquidacionRepository.save(liq);
        SubsidioTramo tramo = tramoRepository.findById(liq.getTramoId()).orElseThrow();
        timelineService.registrar(tramo.getCasoId(), "REVERSION_PLANILLA", motivo, liq.getId());
        return liq;
    }

    private ConceptoPlanilla resolverConcepto(
            Long reglaId, String tipoSubsidio, String tipoImputacion) {
        return reglaConceptoRepository
                .findByReglaVigenciaIdAndTipoSubsidioAndTipoImputacionAndActivo(
                        reglaId, tipoSubsidio, tipoImputacion, 1)
                .map(SubsidioReglaConcepto::getConceptoPlanillaId)
                .flatMap(conceptoRepository::findById)
                .orElseGet(() -> fallbackConcepto(tipoSubsidio, tipoImputacion));
    }

    private ConceptoPlanilla fallbackConcepto(String tipoSubsidio, String tipoImputacion) {
        if (SubsidioEstados.IMPUT_DIFERENCIAL_2073.equals(tipoImputacion)) {
            return conceptoRepository.findByCodigoAndActivo("SUBSIDIO_DIF_CAS", 1)
                    .orElseThrow(() -> new NegocioException("Concepto SUBSIDIO_DIF_CAS no configurado"));
        }
        String codigo = SubsidioEstados.TIPO_MATERNIDAD.equalsIgnoreCase(tipoSubsidio)
                ? "SUBSIDIO_MATERNIDAD" : "SUBSIDIO_ENFERMEDAD";
        return conceptoRepository.findByCodigoAndActivo(codigo, 1)
                .orElseThrow(() -> new NegocioException("Concepto " + codigo + " no configurado"));
    }

    private static String mapTipoSubsidio(String tipoCaso) {
        return SubsidioEstados.TIPO_MATERNIDAD.equalsIgnoreCase(tipoCaso)
                ? SubsidioEstados.TIPO_MATERNIDAD : SubsidioEstados.TIPO_ENFERMEDAD;
    }

    private MovimientoPlanillaDetalle grabarDetalle(
            Long movimientoId, ConceptoPlanilla concepto, BigDecimal monto, String obs) {
        MovimientoPlanillaDetalle det = new MovimientoPlanillaDetalle();
        det.setMovimientoPlanillaId(movimientoId);
        det.setConceptoPlanillaId(concepto.getId());
        det.setMonto(monto.setScale(2, RoundingMode.HALF_UP).doubleValue());
        det.setCantidad(1.0);
        det.setObservacion(obs);
        det.setCreatedAt(LocalDateTime.now());
        return detalleRepository.save(det);
    }
}
