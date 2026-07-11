package com.indeci.rrhh.service.lbs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.lbs.LbsResultDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.PlanillaLote;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PlanillaLoteRepository;
import com.indeci.rrhh.service.GeneradorPlanillaService;
import com.indeci.rrhh.service.ParametroRemunerativoService;
import com.indeci.rrhh.service.cts.CtsCalculadorService;
import com.indeci.rrhh.dto.cts.CtsLiquidacionResponseDto;
import com.indeci.rrhh.dto.TiempoServicioDto;
import com.indeci.rrhh.dto.VacacionCalculoInput;
import com.indeci.rrhh.dto.VacacionCalculoDto;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.VacacionSaldoRepository;
import com.indeci.rrhh.service.TiempoServicioService;
import com.indeci.rrhh.service.VacacionCalculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;

@Service
@RequiredArgsConstructor
public class LbsCalculationService {

    private static final String TIPO_PLANILLA_LBS = "LBS";

    private final EmpleadoPlanillaRepository planillaRepository;
    private final PlanillaLoteRepository planillaLoteRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository movimientoPlanillaDetalleRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final GeneradorPlanillaService motor;
    private final CtsCalculadorService ctsCalculadorService;
    private final TiempoServicioService tiempoServicioService;
    private final VacacionCalculoService vacacionCalculoService;
    private final VacacionSaldoRepository vacacionSaldoRepository;

    @Transactional
    public LbsResultDto generarLbs(String periodo, Long regimenLaboralId) {
        LocalDate inicioPeriodo = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        LocalDate finPeriodo = inicioPeriodo.plusMonths(1).minusDays(1);

        List<LbsResultDto.LbsErrorDto> fallidos = new ArrayList<>();
        int exitosos = 0;
        int total = 0;

        Map<String, Long> loteIdsPorRegimen = new HashMap<>();

        // Traer activos y filtrar en memoria los que cesaron en este mes
        List<EmpleadoPlanilla> candidatos = planillaRepository.findByActivo(1);

        for (EmpleadoPlanilla v : candidatos) {
            if (regimenLaboralId != null && !regimenLaboralId.equals(v.getRegimenLaboralId())) {
                continue;
            }

            LocalDate cese = v.getFechaCese();
            if (cese == null) {
                continue; // No cesado
            }

            if (cese.isBefore(inicioPeriodo) || cese.isAfter(finPeriodo)) {
                continue; // Cesó en otro mes
            }

            total++;

            String regimen = v.getRegimenLaboralId() != null
                    ? motor.resolverRegimenLaboralCodigo(v.getRegimenLaboralId()) : "DESCONOCIDO";

            // Cálculos básicos (POSIBLE_CAMBIO_RRHH: Fórmulas simplificadas, pendientes de validación normativa final)
            BigDecimal baseRemunerativa = motor.resolverBaseRemunerativa(v, periodo);
            
            // 1. Vacaciones No Gozadas y Truncas
            double diasGanadosHistoricos = vacacionSaldoRepository.findByEmpleadoIdInAndActivo(List.of(v.getEmpleadoId()), 1).stream()
                    .mapToDouble(s -> s.getDiasGanados() != null ? s.getDiasGanados() : 0d)
                    .sum();
            double diasGozados = vacacionSaldoRepository.findByEmpleadoIdInAndActivo(List.of(v.getEmpleadoId()), 1).stream()
                    .mapToDouble(s -> s.getDiasGozados() != null ? s.getDiasGozados() : 0d)
                    .sum();

            TiempoServicioDto ts = tiempoServicioService.calcularDesde(List.of(v), v.getEmpleadoId(), cese)
                    .orElseThrow(() -> new NegocioException("ERR_LBS_TIEMPO_SERVICIO|" + v.getEmpleadoId()));

            VacacionCalculoInput vacInput = new VacacionCalculoInput(
                    v.getEmpleadoId(),
                    null, null,
                    ts.anios(), ts.meses(), ts.dias(), ts.totalDias360(),
                    diasGanadosHistoricos, diasGozados,
                    baseRemunerativa,
                    5, 0
            );

            VacacionCalculoDto vacDto = vacacionCalculoService.calcular(vacInput);

            BigDecimal montoVacacionesNoGozadas = vacDto.costoNoGozadas().max(BigDecimal.ZERO);
            BigDecimal montoVacacionesTruncas = vacDto.costoTruncasMes().add(vacDto.costoTruncasDia()).max(BigDecimal.ZERO);
            
            // 2. Aguinaldo Trunco
            // Si el cese es antes de Julio o antes de Diciembre, se paga la parte proporcional
            BigDecimal montoAguinaldoTrunco = BigDecimal.ZERO;
            int mesCese = cese.getMonthValue();
            if ("CAS".equalsIgnoreCase(regimen) || "1057".equalsIgnoreCase(regimen) || "276".equalsIgnoreCase(regimen)) {
                BigDecimal aguinaldoAnual = new BigDecimal("300.00"); // Asumido
                int mesesParaAguinaldo = (mesCese <= 7) ? mesCese : (mesCese - 7);
                montoAguinaldoTrunco = aguinaldoAnual.divide(new BigDecimal("6"), 2, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(mesesParaAguinaldo));
            }

            // 3. CTS Trunca
            BigDecimal montoCtsTrunca = BigDecimal.ZERO;
            if (!"CAS".equalsIgnoreCase(regimen) && !"1057".equalsIgnoreCase(regimen)) {
                try {
                    // Reutiliza el servicio de CTS que no aplica a CAS
                    CtsLiquidacionResponseDto ctsResponse = ctsCalculadorService.calcular(v.getEmpleadoId(), v.getId(), periodo);
                    montoCtsTrunca = ctsResponse.montoTotal();
                } catch (Exception e) {
                    fallidos.add(new LbsResultDto.LbsErrorDto(v.getEmpleadoId(), "Error en cálculo CTS Trunca: " + e.getMessage()));
                    // Continua con LBS sin CTS Trunca si falla (o podría hacer continue y no generar)
                }
            }

            BigDecimal montoLbs = montoVacacionesNoGozadas.add(montoVacacionesTruncas).add(montoAguinaldoTrunco).add(montoCtsTrunca);

            if (montoLbs.signum() <= 0) {
                fallidos.add(new LbsResultDto.LbsErrorDto(v.getEmpleadoId(), "LBS calculada es cero o negativa"));
                continue;
            }

            // Resolver Lote ID (Upsert por régimen)
            Long loteId = loteIdsPorRegimen.computeIfAbsent(regimen, r -> {
                PlanillaLote lote = planillaLoteRepository
                        .findByPeriodoAndRegimenLaboralCodigoAndTipoPlanillaAndCorrelativo(periodo, r, TIPO_PLANILLA_LBS, 1)
                        .orElse(null);

                if (lote == null) {
                    lote = new PlanillaLote();
                    lote.setPeriodo(periodo);
                    lote.setRegimenLaboralCodigo(r);
                    lote.setTipoPlanilla(TIPO_PLANILLA_LBS);
                    lote.setConceptoPlanilla("LBS");
                    lote.setCorrelativo(1);
                    lote.setEstado("GENERADO");
                    lote.setCreadoPor("SISTEMA");
                    lote.setCreadoEn(LocalDateTime.now());
                    lote = planillaLoteRepository.save(lote);
                } else {
                    List<MovimientoPlanilla> movs = movimientoRepository.findByLoteId(lote.getId());
                    for (MovimientoPlanilla m : movs) {
                        movimientoPlanillaDetalleRepository.deleteByMovimientoPlanillaId(m.getId());
                    }
                    movimientoRepository.deleteAll(movs);
                    lote.setCreadoEn(LocalDateTime.now());
                    lote = planillaLoteRepository.save(lote);
                }
                return lote.getId();
            });

            // Guardar movimiento LBS
            MovimientoPlanilla mov = new MovimientoPlanilla();
            mov.setEmpleadoId(v.getEmpleadoId());
            mov.setPeriodo(periodo);
            mov.setTipoPlanilla(TIPO_PLANILLA_LBS);
            mov.setLoteId(loteId);
            mov.setTotalIngresos(montoLbs.doubleValue());
            mov.setTotalDescuentos(0.0);
            mov.setNetoPagar(montoLbs.doubleValue());
            mov.setEstado("GENERADO");
            mov.setActivo(1);
            mov.setCreatedAt(LocalDateTime.now());
            mov.setRegimenLaboralSnapshot(regimen);
            MovimientoPlanilla saved = movimientoRepository.save(mov);

            // Se graban los detalles. En produccin deben existir estos conceptos en ConceptoPlanilla.
            ConceptoPlanilla cVacNoGozada = buscarConceptoPorCodigoInterno("VAC_NO_GOZADA");
            if (cVacNoGozada != null && montoVacacionesNoGozadas.signum() > 0) {
                motor.grabarDetalle(saved.getId(), cVacNoGozada, montoVacacionesNoGozadas, "Vacaciones No Gozadas LBS");
            }

            ConceptoPlanilla cVacaciones = buscarConceptoPorCodigoInterno("VAC_TRUNCAS");
            if (cVacaciones != null && montoVacacionesTruncas.signum() > 0) {
                motor.grabarDetalle(saved.getId(), cVacaciones, montoVacacionesTruncas, "Vacaciones Truncas LBS");
            }

            ConceptoPlanilla cAguinaldo = buscarConceptoPorCodigoInterno("AGUINALDO_TRUNCO");
            if (cAguinaldo != null && montoAguinaldoTrunco.signum() > 0) {
                motor.grabarDetalle(saved.getId(), cAguinaldo, montoAguinaldoTrunco, "Aguinaldo Trunco LBS");
            }

            ConceptoPlanilla cCts = buscarConceptoPorCodigoInterno("CTS_TRUNCA");
            if (cCts != null && montoCtsTrunca.signum() > 0) {
                motor.grabarDetalle(saved.getId(), cCts, montoCtsTrunca, "CTS Trunca LBS");
            }

            exitosos++;
        }

        return new LbsResultDto(exitosos, total, fallidos);
    }

    private ConceptoPlanilla buscarConceptoPorCodigoInterno(String codigo) {
        return conceptoRepository.findByCodigoAndActivo(codigo, 1).orElse(null);
    }

    public Resource generarReportePdf(Long empleadoId, String periodo) {
        try {
            EmpleadoPlanilla vinculo = planillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                    .orElseThrow(() -> new NegocioException("No se encontró vínculo activo para el empleado"));

            String regimen = vinculo.getRegimenLaboralId() != null
                    ? motor.resolverRegimenLaboralCodigo(vinculo.getRegimenLaboralId()) : "DESCONOCIDO";

            boolean isCas = "CAS".equalsIgnoreCase(regimen) || "1057".equalsIgnoreCase(regimen);
            String reporteFile = isCas ? "/reportes/rrhh/lbs_cas.jrxml" : "/reportes/rrhh/lbs_lsc.jrxml";

            java.io.InputStream jrxmlStream = getClass().getResourceAsStream(reporteFile);
            if (jrxmlStream == null) {
                throw new NegocioException("Plantilla Jasper no encontrada: " + reporteFile);
            }

            net.sf.jasperreports.engine.JasperReport jasperReport = 
                net.sf.jasperreports.engine.JasperCompileManager.compileReport(jrxmlStream);

            Map<String, Object> params = new HashMap<>();
            params.put("EMPRESA_NOMBRE", "Instituto Nacional de Defensa Civil (INDECI)");
            params.put("EMPLEADO_NOMBRE", "Empleado " + empleadoId); 
            params.put("DNI", "00000000"); // Mock
            params.put("CARGO", "ESPECIALISTA"); // Mock
            params.put("DEPENDENCIA", "OFICINA DE TECNOLOGÍAS"); // Mock
            params.put("MOTIVO_CESE", "CONCLUSIÓN / RENUNCIA"); // Mock
            params.put("COMPENSACION_ECONOMICA", "S/ 0.00");
            params.put("INCREMENTOS", "S/ 0.00");
            params.put("FECHA_INICIO", "01/01/2020"); // Mock
            params.put("FECHA_CESE", vinculo.getFechaCese() != null ? vinculo.getFechaCese().toString() : "");
            params.put("ENTIDAD_BANCARIA", "BANCO DE LA NACIÓN");
            params.put("NUMERO_CUENTA", "0000-0000-0000");
            params.put("TIEMPO_SERVICIOS", "0 años, 0 meses y 0 días");
            params.put("VACACIONES_NO_GOZADAS", "0 días");
            
            // Buscar movimiento de LBS
            MovimientoPlanilla mov = movimientoRepository.findAllByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1).stream()
                .filter(m -> "LBS".equals(m.getTipoPlanilla()))
                .findFirst()
                .orElse(null);
            
            BigDecimal vacTruncas = BigDecimal.ZERO;
            BigDecimal vacNoGozadas = BigDecimal.ZERO;
            BigDecimal aguinaldoTrunco = BigDecimal.ZERO;
            BigDecimal ctsTrunca = BigDecimal.ZERO;
            BigDecimal neto = BigDecimal.ZERO;

            if (mov != null) {
                neto = BigDecimal.valueOf(mov.getNetoPagar());
                List<MovimientoPlanillaDetalle> detalles = movimientoPlanillaDetalleRepository.findByMovimientoPlanillaId(mov.getId());
                for (MovimientoPlanillaDetalle d : detalles) {
                    if ("VAC_TRUNCAS".equals(d.getConceptoCodigo())) {
                        vacTruncas = BigDecimal.valueOf(d.getMonto());
                    } else if ("VAC_NO_GOZADA".equals(d.getConceptoCodigo())) {
                        vacNoGozadas = BigDecimal.valueOf(d.getMonto());
                    } else if ("AGUINALDO_TRUNCO".equals(d.getConceptoCodigo())) {
                        aguinaldoTrunco = BigDecimal.valueOf(d.getMonto());
                    } else if ("CTS_TRUNCA".equals(d.getConceptoCodigo())) {
                        ctsTrunca = BigDecimal.valueOf(d.getMonto());
                    }
                }
            }

            // Para LSC / 276
            BigDecimal totalVacacional = vacTruncas.add(vacNoGozadas);
            params.put("COMPENSACION_VACACIONAL_TOTAL", "S/ " + String.format("%.2f", totalVacacional));
            params.put("VAC_PENDIENTES_DIAS", vacNoGozadas.signum() > 0 ? "PAGADO" : "0 DÍAS");
            params.put("VAC_PENDIENTES_MONTO", "S/ " + String.format("%.2f", vacNoGozadas));
            params.put("VAC_TRUNCAS_MESES", vacTruncas.signum() > 0 ? "LIQUIDADOS" : "0 MESES");
            params.put("VAC_TRUNCAS_MESES_MONTO", "S/ " + String.format("%.2f", vacTruncas));
            params.put("VAC_TRUNCAS_DIAS", vacTruncas.signum() > 0 ? "LIQUIDADOS" : "0 DÍAS");
            params.put("VAC_TRUNCAS_DIAS_MONTO", "S/ 0.00"); // Combined with meses monto in our model
            params.put("REINTEGROS_TOTAL", "S/ 0.00");
            params.put("REINTEGROS_DETALLE", "");
            params.put("CTS_TOTAL", "S/ " + String.format("%.2f", ctsTrunca));
            params.put("CTS_DETALLE", "");
            params.put("AGUINALDO_TRUNCO_TOTAL", "S/ " + String.format("%.2f", aguinaldoTrunco));
            params.put("AGUINALDO_TRUNCO_DETALLE", "");
            
            // Para CAS
            params.put("TOTAL_OTROS_INGRESOS", "S/ " + String.format("%.2f", aguinaldoTrunco));
            params.put("AGUINALDO_TRUNCO_MONTO", "S/ " + String.format("%.2f", aguinaldoTrunco));
            params.put("DIAS_LABORADOS_MONTO", "S/ 0.00");
            params.put("REINTEGROS_MONTO", "S/ 0.00");
            
            params.put("TOTAL_INGRESOS", "S/ " + String.format("%.2f", neto));
            params.put("DESCUENTOS_DEDUCIBLES", "S/ 0.00");
            params.put("BASE_IMPONIBLE", "S/ " + String.format("%.2f", neto));
            params.put("DESCUENTOS_NO_DEDUCIBLES", "S/ 0.00");
            params.put("MONTO_NETO_PAGAR", "S/ " + String.format("%.2f", neto));
            params.put("APORTES_EMPLEADOR_TOTAL", "S/ 0.00");
            params.put("ESSALUD_9", "S/ 0.00");

            net.sf.jasperreports.engine.JasperPrint jasperPrint = 
                net.sf.jasperreports.engine.JasperFillManager.fillReport(
                    jasperReport, 
                    params, 
                    new net.sf.jasperreports.engine.JREmptyDataSource()
                );

            byte[] pdfBytes = net.sf.jasperreports.engine.JasperExportManager.exportReportToPdf(jasperPrint);
            
            return new org.springframework.core.io.ByteArrayResource(pdfBytes);

        } catch (Exception e) {
            throw new NegocioException("Error al generar reporte PDF de LBS: " + e.getMessage());
        }
    }
}
