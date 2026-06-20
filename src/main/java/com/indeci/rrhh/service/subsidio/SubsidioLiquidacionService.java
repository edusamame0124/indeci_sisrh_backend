package com.indeci.rrhh.service.subsidio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.subsidio.SubsidioLiquidacionExplicacionDto;
import com.indeci.rrhh.dto.subsidio.SubsidioValidacionDto;
import com.indeci.rrhh.entity.SubsidioBaseDetalle;
import com.indeci.rrhh.entity.SubsidioBaseHistorica;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioLiquidacion;
import com.indeci.rrhh.entity.SubsidioReglaFormula;
import com.indeci.rrhh.entity.SubsidioReglaVigencia;
import com.indeci.rrhh.entity.SubsidioTramo;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.repository.SubsidioLiquidacionRepository;
import com.indeci.rrhh.repository.SubsidioReglaFormulaRepository;
import com.indeci.rrhh.repository.SubsidioTramoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.subsidio.SubsidioEstados;
import com.indeci.rrhh.subsidio.formula.SubsidioFormulaContext;
import com.indeci.rrhh.subsidio.formula.SubsidioFormulaEngine;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubsidioLiquidacionService {

    private static final BigDecimal TREINTA = new BigDecimal("30");
    private static final String FORMULA_SUBSIDIO_DIARIO = "SUBSIDIO_DIARIO_ESSALUD";

    private final SubsidioTramoRepository tramoRepository;
    private final SubsidioCasoRepository casoRepository;
    private final SubsidioLiquidacionRepository liquidacionRepository;
    private final SubsidioBaseHistoricaService baseHistoricaService;
    private final SubsidioParametroResolverService parametroResolver;
    private final SubsidioReglaResolverService reglaResolver;
    private final SubsidioReglaFormulaRepository formulaRepository;
    private final SubsidioValidacionService validacionService;
    private final SubsidioTimelineService timelineService;
    private final SubsidioFormulaEngine formulaEngine;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SubsidioLiquidacion calcular(Long tramoId) {
        SubsidioTramo tramo = tramoRepository.findByIdAndActivo(tramoId, 1)
                .orElseThrow(() -> new NegocioException("Tramo no encontrado"));
        SubsidioCaso caso = casoRepository.findByIdAndActivo(tramo.getCasoId(), 1)
                .orElseThrow(() -> new NegocioException("Caso no encontrado"));

        List<SubsidioValidacionDto> validaciones = validacionService.validarCaso(caso.getId());
        validaciones.stream()
                .filter(v -> SubsidioEstados.SEVERIDAD_BLOQUEO.equals(v.severidad()))
                .findFirst()
                .ifPresent(v -> {
                    throw new NegocioException(v.mensaje() + " (" + v.codigo() + ")");
                });

        liquidacionRepository.findByTramoIdAndEsVigente(tramoId, "S").ifPresent(liq -> {
            if (SubsidioEstados.LIQ_APLICADO_PLANILLA.equals(liq.getEstado())) {
                throw new NegocioException(
                        "Ya existe una liquidación vigente aplicada. Recalcule generando nueva versión. (SUB_V004)");
            }
            liquidacionRepository.desactivarVigentesPorTramo(tramoId);
        });

        SubsidioBaseHistorica base = baseHistoricaService.obtenerVigente(caso.getId());
        if (base == null) {
            base = baseHistoricaService.calcular(caso.getId());
        }
        List<SubsidioBaseDetalle> detalles = baseHistoricaService.listarDetalle(base.getId());

        LocalDate fechaRef = tramo.getFechaDesde();
        int anioFiscal = fechaRef.getYear();
        SubsidioReglaVigencia regla = reglaResolver.resolverVigente(fechaRef);
        Map<String, BigDecimal> params = parametroResolver.mapaNumerico(fechaRef, anioFiscal);
        params.put("DIVISOR_PROMEDIO", BigDecimal.valueOf(base.getDivisorPromedio()));

        SubsidioReglaFormula formula = formulaRepository
                .findByReglaVigenciaIdAndCodigoFormulaAndActivo(
                        regla.getId(), FORMULA_SUBSIDIO_DIARIO, 1)
                .orElseThrow(() -> new NegocioException(
                        "Fórmula " + FORMULA_SUBSIDIO_DIARIO + " no configurada para la regla vigente"));

        SubsidioFormulaContext ctx = new SubsidioFormulaContext(
                detalles, params, base.getTopeMensual(), base.getMesesEvaluados());
        BigDecimal subsidioDiario = formulaEngine.evaluar(formula.getExpresionJson(), ctx);

        BigDecimal remuneracionMensual = resolverRemuneracion(caso.getEmpleadoId());
        BigDecimal contraprestacionDiaria = remuneracionMensual
                .divide(TREINTA, 4, RoundingMode.HALF_UP);
        BigDecimal contraprestacionEquivalente = contraprestacionDiaria
                .multiply(BigDecimal.valueOf(tramo.getDiasSubsidio()))
                .setScale(2, RoundingMode.HALF_UP);

        DistribucionDias distribucion = calcularDistribucionDias(caso, tramo, params);
        BigDecimal subsidioEstimado = subsidioDiario
                .multiply(BigDecimal.valueOf(distribucion.diasEssalud()))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal diferencial = contraprestacionEquivalente.subtract(subsidioEstimado)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal conciliacion = contraprestacionEquivalente
                .add(contraprestacionDiaria.multiply(BigDecimal.valueOf(tramo.getDiasLaborados())))
                .setScale(2, RoundingMode.HALF_UP);

        int version = liquidacionRepository.findByTramoIdOrderByVersionLiqDesc(tramoId).stream()
                .mapToInt(SubsidioLiquidacion::getVersionLiq)
                .max().orElse(0) + 1;

        SubsidioLiquidacion liq = new SubsidioLiquidacion();
        liq.setTramoId(tramoId);
        liq.setReglaVigenciaId(regla.getId());
        liq.setParametroVersionIds(serializarIds(parametroResolver.idsVersionVigente(fechaRef)));
        liq.setContraprestacionDiaria(contraprestacionDiaria);
        liq.setDiasLaborados(tramo.getDiasLaborados());
        liq.setContraprestacionEquivalente(contraprestacionEquivalente);
        liq.setSubsidioDiarioEssalud(subsidioDiario);
        liq.setSubsidioEstimado(subsidioEstimado);
        liq.setSubsidioReconocido(subsidioEstimado);
        liq.setDiferencialIndeci(diferencial);
        liq.setConciliacionTotal(conciliacion);
        liq.setVersionLiq(version);
        liq.setEsVigente("S");
        liq.setEstado(SubsidioEstados.LIQ_CALCULADO);
        liq.setFormulaAplicada(FORMULA_SUBSIDIO_DIARIO);
        liq.setSnapshotJson(construirSnapshot(caso, tramo, base, params, distribucion));
        liq.setCreatedAt(LocalDateTime.now());
        liq.setCreatedBy(SecurityUtil.getUsername());
        liq = liquidacionRepository.save(liq);

        tramo.setEstadoTramo(SubsidioEstados.TRAMO_CALCULADO);
        tramoRepository.save(tramo);

        if (!SubsidioEstados.CASO_APLICADO_PLANILLA.equals(caso.getEstado())) {
            caso.setEstado(SubsidioEstados.CASO_CALCULADO);
            caso.setReglaVigenciaId(regla.getId());
            casoRepository.save(caso);
        }

        timelineService.registrar(caso.getId(), "CALCULO_LIQUIDACION",
                "Liquidación v" + version + " tramo " + tramo.getPeriodo(), liq.getId());
        return liq;
    }

    @Transactional(readOnly = true)
    public List<SubsidioLiquidacion> historial(Long tramoId) {
        return liquidacionRepository.findByTramoIdOrderByVersionLiqDesc(tramoId);
    }

    @Transactional(readOnly = true)
    public SubsidioLiquidacionExplicacionDto explicacion(Long liquidacionId) {
        SubsidioLiquidacion liq = liquidacionRepository.findById(liquidacionId)
                .orElseThrow(() -> new NegocioException("Liquidación no encontrada"));
        SubsidioTramo tramo = tramoRepository.findById(liq.getTramoId()).orElseThrow();
        SubsidioCaso caso = casoRepository.findById(tramo.getCasoId()).orElseThrow();
        SubsidioReglaVigencia regla = reglaResolver.resolverVigente(tramo.getFechaDesde());

        return new SubsidioLiquidacionExplicacionDto(
                liq.getId(),
                liq.getVersionLiq(),
                regla.getCodigo() + " v" + regla.getVersion(),
                liq.getFormulaAplicada(),
                liq.getContraprestacionDiaria(),
                liq.getContraprestacionEquivalente(),
                liq.getSubsidioDiarioEssalud(),
                liq.getSubsidioEstimado(),
                liq.getDiferencialIndeci(),
                liq.getConciliacionTotal(),
                tramo.getDiasSubsidio(),
                tramo.getDiasLaborados(),
                caso.getTipoCaso(),
                liq.getSnapshotJson());
    }

    private DistribucionDias calcularDistribucionDias(
            SubsidioCaso caso, SubsidioTramo tramo, Map<String, BigDecimal> params) {
        int diasTramo = tramo.getDiasSubsidio();
        if (SubsidioEstados.TIPO_MATERNIDAD.equalsIgnoreCase(caso.getTipoCaso())) {
            int diasEntidad = Math.min(diasTramo, params.get("DIAS_ENTIDAD_MAT").intValue());
            return new DistribucionDias(diasEntidad, Math.max(0, diasTramo - diasEntidad));
        }
        if (SubsidioEstados.TIPO_ENFERMEDAD.equalsIgnoreCase(caso.getTipoCaso())) {
            int limite = params.get("DIAS_ENTIDAD_ENF").intValue();
            int previos = calcularDiasPreviosEnfermedad(caso, tramo);
            int restantes = Math.max(0, limite - previos);
            int diasEntidad = Math.min(diasTramo, restantes);
            return new DistribucionDias(diasEntidad, Math.max(0, diasTramo - diasEntidad));
        }
        return new DistribucionDias(0, diasTramo);
    }

    private int calcularDiasPreviosEnfermedad(SubsidioCaso caso, SubsidioTramo tramoActual) {
        List<SubsidioTramo> tramos = tramoRepository
                .findByCasoIdAndActivoAndEsVigenteOrderByFechaDesdeAsc(caso.getId(), 1, "S");
        int total = 0;
        for (SubsidioTramo t : tramos) {
            if (t.getFechaDesde().isBefore(tramoActual.getFechaDesde())) {
                total += t.getDiasSubsidio();
            }
        }
        return total;
    }

    private BigDecimal resolverRemuneracion(Long empleadoId) {
        return planillaRepository.findByEmpleadoIdAndActivo(empleadoId, 1).stream()
                .filter(p -> p.getSueldoBasico() != null && p.getSueldoBasico() > 0)
                .map(p -> BigDecimal.valueOf(p.getSueldoBasico()))
                .findFirst()
                .orElseThrow(() -> new NegocioException(
                        "Remuneración mensual base no configurada para el empleado"));
    }

    private String serializarIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String construirSnapshot(
            SubsidioCaso caso, SubsidioTramo tramo, SubsidioBaseHistorica base,
            Map<String, BigDecimal> params, DistribucionDias distribucion) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("casoId", caso.getId());
        snap.put("tipoCaso", caso.getTipoCaso());
        snap.put("tramoId", tramo.getId());
        snap.put("periodo", tramo.getPeriodo());
        snap.put("baseReconocida", base.getBaseReconocida());
        snap.put("diasEntidad", distribucion.diasEntidad());
        snap.put("diasEssalud", distribucion.diasEssalud());
        snap.put("parametros", params);
        try {
            return objectMapper.writeValueAsString(snap);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private record DistribucionDias(int diasEntidad, int diasEssalud) {}
}
