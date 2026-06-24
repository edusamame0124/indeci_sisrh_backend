package com.indeci.rrhh.service.subsidio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.subsidio.SubsidioBaseDetalleInput;
import com.indeci.rrhh.dto.subsidio.SubsidioBaseManualInput;
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
            // Sin historial de planilla en la ventana: se proyectan MESES_BASE
            // detalles uniformes con el respaldo topado. Generar las filas (y no
            // solo baseReconocida) es indispensable: la fórmula del subsidio
            // diario es un SUM_TOP sobre los detalles, así que sin ellos el motor
            // colapsaría a cero (subsidio diario = 0).
            BigDecimal topado = topar(fallbackMensual, topeMensual);
            for (YearMonth ym = desde; !ym.isAfter(hasta); ym = ym.plusMonths(1)) {
                SubsidioBaseDetalle det = new SubsidioBaseDetalle();
                det.setPeriodo(SubsidioPeriodoUtil.deFecha(ym.atDay(1)));
                det.setRemuneracionReal(fallbackMensual);
                det.setTopeAplicado(topeMensual);
                det.setBaseComputable(topado);
                det.setFuenteMovimientoId(null);
                detalles.add(det);
                baseReconocida = baseReconocida.add(topado);
            }
            baseReconocida = baseReconocida.setScale(2, RoundingMode.HALF_UP);
        } else {
            // El tope BIM (UIT × %) es ANUAL: cada mes de la base se topa con el
            // tope vigente de SU propio año, no con el del año de la contingencia
            // (p. ej. meses 2025 → 2407.50; meses 2026 → 2475). Se cachea por año.
            Map<Integer, BigDecimal> topePorAnio = new HashMap<>();
            for (MovimientoPlanilla mov : historial) {
                YearMonth ym = SubsidioPeriodoUtil.parseSubsidio(
                        SubsidioPeriodoUtil.aSubsidio(mov.getPeriodo()));
                BigDecimal topeMes = ym != null
                        ? resolverTopeAnual(ym.getYear(), topePorAnio, topeMensual)
                        : topeMensual;
                BigDecimal real = mov.getTotalIngresos() != null
                        ? BigDecimal.valueOf(mov.getTotalIngresos()) : BigDecimal.ZERO;
                BigDecimal computable = topar(real, topeMes);
                baseReconocida = baseReconocida.add(computable);

                SubsidioBaseDetalle det = new SubsidioBaseDetalle();
                det.setPeriodo(SubsidioPeriodoUtil.aSubsidio(mov.getPeriodo()));
                det.setRemuneracionReal(real);
                det.setTopeAplicado(topeMes);
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

    /** Base no persistida + sus filas — usada por el borrador editable y por guardarManual. */
    public record BaseHistoricaPreview(
            SubsidioBaseHistorica base, List<SubsidioBaseDetalle> detalles) {}

    /**
     * Arma el borrador editable de la base: una fila por mes de la ventana de 12
     * meses, precargada con el {@link MovimientoPlanilla} real si existe (MIXTA) o
     * en blanco para que RR. HH. la complete (MANUAL). No persiste nada.
     */
    @Transactional(readOnly = true)
    public BaseHistoricaPreview prepararBorrador(Long casoId) {
        SubsidioCaso caso = casoRepository.findByIdAndActivo(casoId, 1)
                .orElseThrow(() -> new NegocioException("Caso de subsidio no encontrado"));
        LocalDate fechaBase = caso.getFechaContingencia() != null
                ? caso.getFechaContingencia() : caso.getFechaInicio();
        int anioFiscal = fechaBase.getYear();
        var params = parametroResolver.mapaNumerico(fechaBase, anioFiscal);
        BigDecimal topeContingencia = params.get("TOPE_MENSUAL");
        int divisor = parametroResolver
                .obtenerNumerico("DIVISOR_PROMEDIO", fechaBase, anioFiscal).intValue();

        YearMonth eventoYm = YearMonth.from(fechaBase);
        YearMonth desde = eventoYm.minusMonths(MESES_BASE);
        YearMonth hasta = eventoYm.minusMonths(1);

        Map<String, MovimientoPlanilla> porPeriodo = new HashMap<>();
        for (MovimientoPlanilla m : movimientoRepository
                .findByEmpleadoIdAndActivo(caso.getEmpleadoId(), 1)) {
            if (estaEnVentana(m.getPeriodo(), desde, hasta)) {
                porPeriodo.put(SubsidioPeriodoUtil.aSubsidio(m.getPeriodo()), m);
            }
        }

        Map<Integer, BigDecimal> topePorAnio = new HashMap<>();
        List<SubsidioBaseDetalle> detalles = new ArrayList<>();
        BigDecimal baseReconocida = BigDecimal.ZERO;
        boolean algunoPlanilla = false;
        for (YearMonth ym = desde; !ym.isAfter(hasta); ym = ym.plusMonths(1)) {
            String periodo = SubsidioPeriodoUtil.deFecha(ym.atDay(1));
            BigDecimal tope = resolverTopeAnual(ym.getYear(), topePorAnio, topeContingencia);
            MovimientoPlanilla mov = porPeriodo.get(periodo);
            BigDecimal real;
            Long movId = null;
            String esManual;
            if (mov != null) {
                real = mov.getTotalIngresos() != null
                        ? BigDecimal.valueOf(mov.getTotalIngresos()) : BigDecimal.ZERO;
                movId = mov.getId();
                esManual = "N";
                algunoPlanilla = true;
            } else {
                real = BigDecimal.ZERO; // en blanco: RR. HH. lo completa
                esManual = "S";
            }
            BigDecimal computable = topar(real, tope);
            baseReconocida = baseReconocida.add(computable);

            SubsidioBaseDetalle det = new SubsidioBaseDetalle();
            det.setPeriodo(periodo);
            det.setRemuneracionReal(real.setScale(2, RoundingMode.HALF_UP));
            det.setTopeAplicado(tope);
            det.setBaseComputable(computable.setScale(2, RoundingMode.HALF_UP));
            det.setIncidencia("NORMAL");
            det.setEsManual(esManual);
            det.setFuenteMovimientoId(movId);
            detalles.add(det);
        }

        SubsidioBaseHistorica base = new SubsidioBaseHistorica();
        base.setCasoId(casoId);
        base.setMesesEvaluados(detalles.size());
        base.setDivisorPromedio(divisor);
        base.setTopeMensual(topeContingencia);
        base.setBaseReconocida(baseReconocida.setScale(2, RoundingMode.HALF_UP));
        base.setFuente(algunoPlanilla ? "MIXTA" : "MANUAL");
        base.setVersionBase(0); // 0 = borrador no persistido
        base.setEsVigente("N");
        base.setCreatedAt(LocalDateTime.now());
        return new BaseHistoricaPreview(base, detalles);
    }

    /**
     * Persiste una base histórica MANUAL: aplica el tope por año a cada mes
     * (salvo override), respeta el monto real (LSGR = 0 o parcial, no se fuerza),
     * resuelve el divisor según SUBSIDIO_DIVISOR_MODO y versiona la base.
     */
    @Transactional
    public SubsidioBaseHistorica guardarManual(Long casoId, SubsidioBaseManualInput input) {
        SubsidioCaso caso = casoRepository.findByIdAndActivo(casoId, 1)
                .orElseThrow(() -> new NegocioException("Caso de subsidio no encontrado"));
        if (input == null || input.detalles() == null || input.detalles().isEmpty()) {
            throw new NegocioException("La base manual requiere al menos un mes");
        }
        LocalDate fechaBase = caso.getFechaContingencia() != null
                ? caso.getFechaContingencia() : caso.getFechaInicio();
        int anioFiscal = fechaBase.getYear();
        SubsidioReglaVigencia regla = reglaResolver.resolverVigente(fechaBase);
        var params = parametroResolver.mapaNumerico(fechaBase, anioFiscal);
        BigDecimal topeContingencia = params.get("TOPE_MENSUAL");
        int divisorParam = parametroResolver
                .obtenerNumerico("DIVISOR_PROMEDIO", fechaBase, anioFiscal).intValue();
        String divisorModo = parametroResolver.obtenerTexto("SUBSIDIO_DIVISOR_MODO", fechaBase);

        baseRepository.desactivarVigentesPorCaso(casoId);
        int version = baseRepository.findByCasoIdOrderByVersionBaseDesc(casoId).stream()
                .mapToInt(SubsidioBaseHistorica::getVersionBase)
                .max().orElse(0) + 1;

        Map<Integer, BigDecimal> topePorAnio = new HashMap<>();
        Set<String> periodosVistos = new HashSet<>();
        List<SubsidioBaseDetalle> detalles = new ArrayList<>();
        BigDecimal baseReconocida = BigDecimal.ZERO;

        for (SubsidioBaseDetalleInput in : input.detalles()) {
            String periodo = SubsidioPeriodoUtil.aSubsidio(in.periodo());
            YearMonth ym = SubsidioPeriodoUtil.parseSubsidio(periodo);
            if (ym == null) {
                throw new NegocioException("Periodo inválido en la base: " + in.periodo());
            }
            if (!periodosVistos.add(periodo)) {
                throw new NegocioException("Periodo duplicado en la base: " + periodo);
            }
            BigDecimal real = in.remuneracionReal() != null
                    ? in.remuneracionReal() : BigDecimal.ZERO;
            if (real.signum() < 0) {
                throw new NegocioException("Remuneración negativa en el periodo " + periodo);
            }
            BigDecimal tope = in.topeAplicadoOverride() != null
                    ? in.topeAplicadoOverride()
                    : resolverTopeAnual(ym.getYear(), topePorAnio, topeContingencia);
            BigDecimal computable = in.baseComputableOverride() != null
                    ? in.baseComputableOverride()
                    : topar(real, tope);
            baseReconocida = baseReconocida.add(computable);

            SubsidioBaseDetalle det = new SubsidioBaseDetalle();
            det.setPeriodo(periodo);
            det.setRemuneracionReal(real.setScale(2, RoundingMode.HALF_UP));
            det.setTopeAplicado(tope);
            det.setBaseComputable(computable.setScale(2, RoundingMode.HALF_UP));
            det.setIncidencia(in.incidencia() != null ? in.incidencia().toUpperCase() : "NORMAL");
            det.setEsManual("S");
            det.setFuenteMovimientoId(null);
            detalles.add(det);
        }
        baseReconocida = baseReconocida.setScale(2, RoundingMode.HALF_UP);

        // FIJO_360: divisor fijo (los meses LSGR/0 siguen contando como 12×30).
        // PROPORCIONAL: meses presentes × 30 (afiliación < 12 meses).
        int divisor = "PROPORCIONAL".equalsIgnoreCase(divisorModo)
                ? detalles.size() * 30 : divisorParam;

        SubsidioBaseHistorica base = new SubsidioBaseHistorica();
        base.setCasoId(casoId);
        base.setReglaVigenciaId(regla.getId());
        base.setMesesEvaluados(detalles.size());
        base.setDivisorPromedio(divisor);
        base.setTopeMensual(topeContingencia);
        base.setBaseReconocida(baseReconocida);
        base.setFuente("MANUAL");
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

    /**
     * Tope mensual BIM (UIT × %) vigente para el año dado, cacheado. Si el
     * resolutor no tiene un valor para ese año, usa el de la contingencia.
     */
    private BigDecimal resolverTopeAnual(
            int anio, Map<Integer, BigDecimal> cache, BigDecimal porDefecto) {
        return cache.computeIfAbsent(anio, a -> {
            BigDecimal tope = parametroResolver
                    .mapaNumerico(LocalDate.of(a, 6, 1), a)
                    .get("TOPE_MENSUAL");
            return tope != null ? tope : porDefecto;
        });
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
