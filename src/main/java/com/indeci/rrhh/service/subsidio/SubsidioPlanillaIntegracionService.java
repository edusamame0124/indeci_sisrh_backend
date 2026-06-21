package com.indeci.rrhh.service.subsidio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioLiquidacion;
import com.indeci.rrhh.entity.SubsidioLiquidacionMovimiento;
import com.indeci.rrhh.entity.SubsidioReglaConcepto;
import com.indeci.rrhh.entity.SubsidioReglaVigencia;
import com.indeci.rrhh.entity.SubsidioTramo;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
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
    private final MovimientoPlanillaRepository movimientoRepository;
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

        MovimientoPlanilla movimiento = movimientoRepository
                .findByEmpleadoIdAndPeriodoAndActivo(caso.getEmpleadoId(), periodoPlanilla, 1)
                .orElseGet(() -> crearCabecera(caso.getEmpleadoId(), periodoPlanilla));

        SubsidioReglaVigencia regla = reglaResolver.resolverVigente(tramo.getFechaDesde());
        String tipoConcepto = mapTipoSubsidio(caso.getTipoCaso());

        ConceptoPlanilla conceptoSubsidio = resolverConcepto(
                regla.getId(), tipoConcepto, SubsidioEstados.IMPUT_SUBSIDIO_100);
        ConceptoPlanilla conceptoDiferencial = resolverConcepto(
                regla.getId(), "DIFERENCIAL", SubsidioEstados.IMPUT_DIFERENCIAL_2073);

        MovimientoPlanillaDetalle detSubsidio = grabarDetalle(
                movimiento.getId(), conceptoSubsidio,
                liq.getContraprestacionEquivalente(),
                "Subsidio 100% tramo " + tramo.getPeriodo());
        registrarMovimientoSubsidio(liq.getId(), movimiento.getId(), detSubsidio.getId(),
                conceptoSubsidio.getId(), SubsidioEstados.IMPUT_SUBSIDIO_100,
                liq.getContraprestacionEquivalente());

        if (liq.getDiferencialIndeci() != null && liq.getDiferencialIndeci().signum() != 0) {
            MovimientoPlanillaDetalle detDiff = grabarDetalle(
                    movimiento.getId(), conceptoDiferencial,
                    liq.getDiferencialIndeci(),
                    "Diferencial INDECI tramo " + tramo.getPeriodo());
            registrarMovimientoSubsidio(liq.getId(), movimiento.getId(), detDiff.getId(),
                    conceptoDiferencial.getId(), SubsidioEstados.IMPUT_DIFERENCIAL_2073,
                    liq.getDiferencialIndeci());
        }

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

    private MovimientoPlanilla crearCabecera(Long empleadoId, String periodo) {
        MovimientoPlanilla mov = new MovimientoPlanilla();
        mov.setEmpleadoId(empleadoId);
        mov.setPeriodo(periodo);
        mov.setObservacion("SUBSIDIO APLICADO");
        mov.setActivo(1);
        mov.setEstado("GENERADO");
        mov.setCreatedAt(LocalDateTime.now());
        mov.setTotalIngresos(0.0);
        mov.setTotalDescuentos(0.0);
        mov.setNetoPagar(0.0);
        return movimientoRepository.save(mov);
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

    private void registrarMovimientoSubsidio(
            Long liquidacionId, Long movimientoId, Long detId,
            Long conceptoId, String tipoImputacion, BigDecimal monto) {
        SubsidioLiquidacionMovimiento link = new SubsidioLiquidacionMovimiento();
        link.setLiquidacionId(liquidacionId);
        link.setMovimientoPlanillaId(movimientoId);
        link.setMovimientoDetId(detId);
        link.setConceptoPlanillaId(conceptoId);
        link.setTipoImputacion(tipoImputacion);
        link.setMonto(monto);
        link.setEstado("IMPUTADO");
        link.setCreatedAt(LocalDateTime.now());
        movimientoSubsidioRepository.save(link);
    }
}
