package com.indeci.rrhh.service.cts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.DiasNoComputablesDto;
import com.indeci.rrhh.dto.cts.CtsRegularResultDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PlanillaLote;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PlanillaLoteRepository;
import com.indeci.rrhh.service.GeneradorPlanillaService;
import com.indeci.rrhh.service.ParametroRemunerativoService;
import com.indeci.rrhh.service.cts.CtsTiempoServiciosCalculator.TiempoServicios;
import com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;

@Service
@RequiredArgsConstructor
public class CtsRegularCalculationService {

    private static final String TIPO_PLANILLA_CTS = "CTS_REGULAR";
    private static final BigDecimal DOCE = BigDecimal.valueOf(12);
    private static final BigDecimal TREINTA = BigDecimal.valueOf(30);
    private static final int ESCALA_INTERNA = 10;

    private final EmpleadoPlanillaRepository planillaRepository;
    private final PlanillaLoteRepository planillaLoteRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository movimientoPlanillaDetalleRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final GeneradorPlanillaService motor;
    private final CtsTiempoServiciosCalculator tiempoCalculator;
    private final IncidenciaLaboralCompuesta incidenciaLaboralCompuesta;

    @Transactional
    public CtsRegularResultDto generarCts(String periodo, Long regimenLaboralId) {
        LocalDate fp = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        int mes = fp.getMonthValue();

        if (mes != 5 && mes != 11) {
            throw new NegocioException("La CTS Regular solo se genera en Mayo (05) o Noviembre (11). Período: " + periodo);
        }

        LocalDate[] semestre = resolverSemestre(fp);
        LocalDate inicioSemestre = semestre[0];
        LocalDate finSemestre = semestre[1];

        List<CtsRegularResultDto.CtsErrorDto> fallidos = new ArrayList<>();
        int exitosos = 0;
        int total = 0;

        Map<String, Long> loteIdsPorRegimen = new HashMap<>();

        // Traer personal activo
        List<EmpleadoPlanilla> candidatos = planillaRepository.findByActivo(1);

        for (EmpleadoPlanilla v : candidatos) {
            if (regimenLaboralId != null && !regimenLaboralId.equals(v.getRegimenLaboralId())) {
                continue;
            }

            String regimen = v.getRegimenLaboralId() != null
                    ? motor.resolverRegimenLaboralCodigo(v.getRegimenLaboralId()) : "DESCONOCIDO";

            // Restricción: CAS no tiene CTS por norma.
            if ("CAS".equalsIgnoreCase(regimen) || "1057".equalsIgnoreCase(regimen)) {
                continue;
            }

            total++;

            // Art. 8 TUO Ley de CTS: se devenga por tiempo EFECTIVAMENTE trabajado dentro del
            // semestre (D-1: ancla al ingreso si es posterior al inicio del semestre), neto de
            // LSG + faltas injustificadas (D-2: misma fuente que Vacaciones — los descansos
            // médicos/maternidad SÍ computan porque están sembrados con es_sin_goce=0).
            BigDecimal baseRemunerativa = motor.resolverBaseRemunerativa(v, periodo);
            TiempoComputableResult tiempoResult = calcularTiempoComputable(v, inicioSemestre, finSemestre);
            TiempoServicios t = tiempoResult.tiempo();
            BigDecimal montoMeses = baseRemunerativa
                    .divide(DOCE, ESCALA_INTERNA, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(t.meses()))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal montoDias = baseRemunerativa
                    .divide(DOCE, ESCALA_INTERNA, java.math.RoundingMode.HALF_UP)
                    .divide(TREINTA, ESCALA_INTERNA, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(t.dias()))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal montoCts = montoMeses.add(montoDias);

            if (montoCts.signum() <= 0) {
                fallidos.add(new CtsRegularResultDto.CtsErrorDto(v.getEmpleadoId(), "Monto CTS calculado es cero o negativo"));
                continue;
            }

            // Resolver Lote ID (Upsert por régimen)
            Long loteId = loteIdsPorRegimen.computeIfAbsent(regimen, r -> {
                PlanillaLote lote = planillaLoteRepository
                        .findByPeriodoAndRegimenLaboralCodigoAndTipoPlanillaAndCorrelativo(periodo, r, TIPO_PLANILLA_CTS, 1)
                        .orElse(null);

                if (lote == null) {
                    lote = new PlanillaLote();
                    lote.setPeriodo(periodo);
                    lote.setRegimenLaboralCodigo(r);
                    lote.setTipoPlanilla(TIPO_PLANILLA_CTS);
                    lote.setConceptoPlanilla("CTS_REGULAR");
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

            // Guardar movimiento CTS
            MovimientoPlanilla mov = new MovimientoPlanilla();
            mov.setEmpleadoId(v.getEmpleadoId());
            mov.setPeriodo(periodo);
            mov.setTipoPlanilla(TIPO_PLANILLA_CTS);
            mov.setLoteId(loteId);
            mov.setTotalIngresos(montoCts.doubleValue());
            mov.setTotalDescuentos(0.0);
            mov.setNetoPagar(montoCts.doubleValue());
            mov.setEstado("GENERADO");
            mov.setActivo(1);
            mov.setCreatedAt(LocalDateTime.now());
            mov.setRegimenLaboralSnapshot(regimen);
            mov.setObservacion(describirPeriodoComputable(tiempoResult));
            MovimientoPlanilla saved = movimientoRepository.save(mov);

            ConceptoPlanilla cCts = buscarConceptoPorCodigoInterno("CTS_REGULAR");
            if (cCts != null) {
                motor.grabarDetalle(saved.getId(), cCts, montoCts, "CTS Regular (Semestral)");
            }

            exitosos++;
        }

        return new CtsRegularResultDto(exitosos, total, fallidos);
    }

    private ConceptoPlanilla buscarConceptoPorCodigoInterno(String codigo) {
        return conceptoRepository.findByCodigoAndActivo(codigo, 1).orElse(null);
    }

    /**
     * Semestre computable de la CTS Regular según el mes del período (D.S. N° 001-97-TR):
     * Mayo → 01-Nov (año anterior) a 30-Abr; Noviembre → 01-May a 31-Oct (mismo año).
     */
    private LocalDate[] resolverSemestre(LocalDate fechaPeriodo) {
        int anio = fechaPeriodo.getYear();
        if (fechaPeriodo.getMonthValue() == 5) {
            return new LocalDate[] { LocalDate.of(anio - 1, 11, 1), LocalDate.of(anio, 4, 30) };
        }
        return new LocalDate[] { LocalDate.of(anio, 5, 1), LocalDate.of(anio, 10, 31) };
    }

    /** Tiempo computable EFECTIVO + desglose de incidencias que lo originaron. */
    private record TiempoComputableResult(TiempoServicios tiempo, DiasNoComputablesDto noComputables) {}

    /**
     * Tiempo computable EFECTIVO del vínculo dentro del semestre (D-1 prorrateo anclado,
     * D-2 neto de LSG + faltas). Reutiliza {@link CtsTiempoServiciosCalculator}, la misma
     * calculadora pura que usa la CTS Trunca — mismo convenio 30/360, mismo redondeo.
     */
    private TiempoComputableResult calcularTiempoComputable(
            EmpleadoPlanilla v, LocalDate inicioSemestre, LocalDate finSemestre) {
        LocalDate ingreso = resolverIngreso(v);
        LocalDate ancla = (ingreso != null && ingreso.isAfter(inicioSemestre)) ? ingreso : inicioSemestre;

        TiempoServicios ideal = tiempoCalculator.computar(ancla, finSemestre);
        DiasNoComputablesDto noComp = incidenciaLaboralCompuesta
                .calcularDesglose(v.getEmpleadoId(), ancla, finSemestre);
        TiempoServicios real = tiempoCalculator.descontar(ideal, noComp.total());
        return new TiempoComputableResult(real, noComp);
    }

    /** Ancla de ingreso del vínculo (mismo orden de fallback que CTS Trunca). */
    private LocalDate resolverIngreso(EmpleadoPlanilla v) {
        if (v.getFechaInicioContrato() != null) return v.getFechaInicioContrato();
        if (v.getFechaIngreso() != null) return v.getFechaIngreso();
        return v.getFechaInicio();
    }

    /**
     * Tiempo neto + desglose de LSG/faltas que lo originaron (Transparencia — directriz
     * UI/UX, naming humanizado para el empleado final), p. ej.
     * {@code "5m 25d (Días descontados: LSG 5d)"}. Sin desglose si no hubo incidencias,
     * p. ej. {@code "6m 0d"}.
     */
    private String describirTiempoComputable(TiempoComputableResult r) {
        String base = r.tiempo().meses() + "m " + r.tiempo().dias() + "d";
        DiasNoComputablesDto n = r.noComputables();
        if (n.total() <= 0) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base).append(" (Días descontados: ");
        boolean any = false;
        if (n.lsg() > 0) {
            sb.append("LSG ").append(n.lsg()).append("d");
            any = true;
        }
        if (n.faltas() > 0) {
            if (any) sb.append(", ");
            sb.append("Faltas ").append(n.faltas()).append("d");
        }
        return sb.append(")").toString();
    }

    /** Nota de trazabilidad visible en el listado de movimientos (columna Observación). */
    private String describirPeriodoComputable(TiempoComputableResult r) {
        return "Tiempo Efectivo: " + describirTiempoComputable(r);
    }

    public Resource generarReportePdf(Long empleadoId, String periodo) {
        try {
            EmpleadoPlanilla vinculo = planillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                    .orElseThrow(() -> new NegocioException("No se encontró vínculo activo para el empleado"));

            String reporteFile = "/reportes/rrhh/cts_regular.jrxml";

            java.io.InputStream jrxmlStream = getClass().getResourceAsStream(reporteFile);
            if (jrxmlStream == null) {
                throw new NegocioException("Plantilla Jasper no encontrada: " + reporteFile);
            }

            net.sf.jasperreports.engine.JasperReport jasperReport = 
                net.sf.jasperreports.engine.JasperCompileManager.compileReport(jrxmlStream);

            Map<String, Object> params = new HashMap<>();
            params.put("EMPRESA_NOMBRE", "INSTITUTO NACIONAL DE DEFENSA CIVIL");
            params.put("EMPRESA_RUC", "20135890031");
            params.put("EMPRESA_DOMICILIO", "CALLE RICARDO ANGULO 694 - SAN ISIDRO");
            params.put("REPRESENTANTE", "CORNEJO VALVERDE ALDO MARTÍN ROBERTO");
            params.put("EMPLEADO_NOMBRE", "Empleado " + empleadoId); // Mock
            params.put("EMPLEADO_DNI", "00000000"); // Mock
            params.put("FECHA_DEPOSITO", "15 de Mayo del 2026"); // Mock o deducido
            params.put("NUMERO_CUENTA", "0000-0000-0000");
            params.put("ENTIDAD_BANCARIA", "SCOTIABANK");

            // Periodo computable real (Art. 8 TUO Ley de CTS) — reemplaza el mock hardcodeado.
            java.time.format.DateTimeFormatter fmtCorto =
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate fp = ParametroRemunerativoService.periodoToFechaInicio(periodo);
            LocalDate[] semestre = resolverSemestre(fp);
            LocalDate inicioSemestre = semestre[0];
            LocalDate finSemestre = semestre[1];
            params.put("PERIODO_DEL", inicioSemestre.format(fmtCorto));
            params.put("PERIODO_AL", finSemestre.format(fmtCorto));

            TiempoComputableResult tiempoPdf = calcularTiempoComputable(vinculo, inicioSemestre, finSemestre);
            params.put("DIAS_COMPUTABLES", describirTiempoComputable(tiempoPdf));

            // Buscar movimiento CTS
            MovimientoPlanilla mov = movimientoRepository.findAllByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1).stream()
                .filter(m -> "CTS_REGULAR".equals(m.getTipoPlanilla()))
                .findFirst()
                .orElse(null);

            BigDecimal neto = BigDecimal.ZERO;
            if (mov != null) {
                neto = BigDecimal.valueOf(mov.getNetoPagar());
            }

            // Remuneración computable real (misma fuente que usa el cálculo, ya no una
            // reversión de "neto × 2" — ese supuesto dejó de ser válido con el prorrateo).
            BigDecimal remun = motor.resolverBaseRemunerativa(vinculo, periodo);

            params.put("SUELDO_BASICO", String.format("%.2f", remun));
            params.put("ASIGNACION_FAMILIAR", "0.00");
            params.put("GRATIFICACION_ORDINARIA", "0.00");
            params.put("PROMEDIO_HORAS_EXTRAS", "0.00");
            params.put("PROMEDIO_COMISIONES", "0.00");
            params.put("TOTAL_REMUNERACION", "S/ " + String.format("%.2f", remun));

            params.put("TOTAL_A_PAGAR", "S/ " + String.format("%.2f", neto));
            
            // Fecha impresión formato "Lima - viernes, 08 Mayo, 2026"
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMMM, yyyy");
            String fechaImpresion = "Lima - " + java.time.LocalDate.now().format(formatter);
            params.put("FECHA_IMPRESION", fechaImpresion);

            net.sf.jasperreports.engine.JasperPrint jasperPrint = 
                net.sf.jasperreports.engine.JasperFillManager.fillReport(
                    jasperReport, 
                    params, 
                    new net.sf.jasperreports.engine.JREmptyDataSource()
                );

            byte[] pdfBytes = net.sf.jasperreports.engine.JasperExportManager.exportReportToPdf(jasperPrint);
            
            return new org.springframework.core.io.ByteArrayResource(pdfBytes);

        } catch (Exception e) {
            throw new NegocioException("Error al generar reporte PDF de CTS Regular: " + e.getMessage());
        }
    }
}
