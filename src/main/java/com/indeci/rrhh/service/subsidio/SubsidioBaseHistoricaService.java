package com.indeci.rrhh.service.subsidio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.SubsidioBaseDetalle;
import com.indeci.rrhh.entity.SubsidioBaseHistorica;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioReglaVigencia;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.SubsidioBaseDetalleRepository;
import com.indeci.rrhh.repository.SubsidioBaseHistoricaRepository;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.subsidio.SubsidioPeriodoUtil;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubsidioBaseHistoricaService {

    private static final int MESES_BASE = 12;

    private final SubsidioCasoRepository casoRepository;
    private final SubsidioBaseHistoricaRepository baseRepository;
    private final SubsidioBaseDetalleRepository detalleRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final SubsidioParametroResolverService parametroResolver;
    private final SubsidioReglaResolverService reglaResolver;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SubsidioBaseHistorica calcular(Long casoId) {
        SubsidioCaso caso = casoRepository.findByIdAndActivo(casoId, 1)
                .orElseThrow(() -> new NegocioException("Caso de subsidio no encontrado"));

        LocalDate fechaBase = caso.getFechaContingencia() != null
                ? caso.getFechaContingencia() : caso.getFechaInicio();
        int anioFiscal = fechaBase.getYear();
        SubsidioReglaVigencia regla = reglaResolver.resolverVigente(fechaBase);
        var params = parametroResolver.mapaNumerico(fechaBase, anioFiscal);
        BigDecimal topeMensual = params.get("TOPE_MENSUAL");
        int divisor = parametroResolver.obtenerNumerico("DIVISOR_PROMEDIO", fechaBase, anioFiscal).intValue();

        baseRepository.desactivarVigentesPorCaso(casoId);
        int version = baseRepository.findByCasoIdOrderByVersionBaseDesc(casoId).stream()
                .mapToInt(SubsidioBaseHistorica::getVersionBase)
                .max().orElse(0) + 1;

        YearMonth eventoYm = YearMonth.from(fechaBase);
        YearMonth desde = eventoYm.minusMonths(MESES_BASE);
        YearMonth hasta = eventoYm.minusMonths(1);

        List<MovimientoPlanilla> historial = movimientoRepository
                .findByEmpleadoIdAndActivo(caso.getEmpleadoId(), 1)
                .stream()
                .filter(m -> estaEnVentana(m.getPeriodo(), desde, hasta))
                .toList();

        BigDecimal fallbackMensual = resolverRemuneracionFallback(caso.getEmpleadoId());
        List<SubsidioBaseDetalle> detalles = new ArrayList<>();
        BigDecimal baseReconocida = BigDecimal.ZERO;

        if (historial.isEmpty()) {
            BigDecimal topado = topar(fallbackMensual, topeMensual);
            baseReconocida = topado.multiply(BigDecimal.valueOf(MESES_BASE))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            for (MovimientoPlanilla mov : historial) {
                BigDecimal real = mov.getTotalIngresos() != null
                        ? BigDecimal.valueOf(mov.getTotalIngresos()) : BigDecimal.ZERO;
                BigDecimal computable = topar(real, topeMensual);
                baseReconocida = baseReconocida.add(computable);

                SubsidioBaseDetalle det = new SubsidioBaseDetalle();
                det.setPeriodo(SubsidioPeriodoUtil.aSubsidio(mov.getPeriodo()));
                det.setRemuneracionReal(real);
                det.setTopeAplicado(topeMensual);
                det.setBaseComputable(computable);
                det.setFuenteMovimientoId(mov.getId());
                detalles.add(det);
            }
            baseReconocida = baseReconocida.setScale(2, RoundingMode.HALF_UP);
        }

        SubsidioBaseHistorica base = new SubsidioBaseHistorica();
        base.setCasoId(casoId);
        base.setReglaVigenciaId(regla.getId());
        base.setMesesEvaluados(historial.isEmpty() ? MESES_BASE : historial.size());
        base.setDivisorPromedio(divisor);
        base.setTopeMensual(topeMensual);
        base.setBaseReconocida(baseReconocida);
        base.setFuente(historial.isEmpty() ? "PARAMETRO" : "PLANILLA");
        base.setVersionBase(version);
        base.setEsVigente("S");
        base.setCreatedAt(LocalDateTime.now());
        try {
            base.setSnapshotJson(objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException ex) {
            base.setSnapshotJson("{}");
        }
        base = baseRepository.save(base);

        for (SubsidioBaseDetalle det : detalles) {
            det.setBaseHistoricaId(base.getId());
            detalleRepository.save(det);
        }
        return base;
    }

    @Transactional(readOnly = true)
    public SubsidioBaseHistorica obtenerVigente(Long casoId) {
        return baseRepository.findByCasoIdAndEsVigente(casoId, "S")
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SubsidioBaseDetalle> listarDetalle(Long baseId) {
        return detalleRepository.findByBaseHistoricaIdOrderByPeriodoAsc(baseId);
    }

    private BigDecimal resolverRemuneracionFallback(Long empleadoId) {
        return planillaRepository.findByEmpleadoIdAndActivo(empleadoId, 1).stream()
                .filter(p -> p.getSueldoBasico() != null && p.getSueldoBasico() > 0)
                .map(p -> BigDecimal.valueOf(p.getSueldoBasico()))
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static boolean estaEnVentana(String periodo, YearMonth desde, YearMonth hasta) {
        YearMonth ym = SubsidioPeriodoUtil.parseSubsidio(
                SubsidioPeriodoUtil.aSubsidio(periodo));
        return ym != null && !ym.isBefore(desde) && !ym.isAfter(hasta);
    }

    private static BigDecimal topar(BigDecimal monto, BigDecimal tope) {
        if (monto == null || monto.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return monto.min(tope);
    }
}
