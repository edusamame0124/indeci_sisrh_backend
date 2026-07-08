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

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;

@Service
@RequiredArgsConstructor
public class CtsRegularCalculationService {

    private static final String TIPO_PLANILLA_CTS = "CTS_REGULAR";

    private final EmpleadoPlanillaRepository planillaRepository;
    private final PlanillaLoteRepository planillaLoteRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository movimientoPlanillaDetalleRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final GeneradorPlanillaService motor;

    @Transactional
    public CtsRegularResultDto generarCts(String periodo, Long regimenLaboralId) {
        LocalDate fp = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        int mes = fp.getMonthValue();

        if (mes != 5 && mes != 11) {
            throw new NegocioException("La CTS Regular solo se genera en Mayo (05) o Noviembre (11). Período: " + periodo);
        }

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

            // Cálculos básicos (POSIBLE_CAMBIO_RRHH: Fórmulas simplificadas)
            BigDecimal baseRemunerativa = motor.resolverBaseRemunerativa(v, periodo);
            BigDecimal montoCts = baseRemunerativa.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP); // Asumido: medio sueldo por semestre

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
            
            // Periodo
            if (periodo.endsWith("05")) {
                params.put("PERIODO_DEL", "01/11/2025"); // Mock: semestre anterior
                params.put("PERIODO_AL", "30/04/2026");
            } else {
                params.put("PERIODO_DEL", "01/05/2026");
                params.put("PERIODO_AL", "31/10/2026");
            }

            // Buscar movimiento CTS
            MovimientoPlanilla mov = movimientoRepository.findAllByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1).stream()
                .filter(m -> "CTS_REGULAR".equals(m.getTipoPlanilla()))
                .findFirst()
                .orElse(null);
            
            BigDecimal neto = BigDecimal.ZERO;
            if (mov != null) {
                neto = BigDecimal.valueOf(mov.getNetoPagar());
            }

            // Mock Remuneración (usualmente se detalla de los haberes base)
            BigDecimal remun = neto.multiply(new BigDecimal("2")); // Si CTS es medio sueldo
            
            params.put("SUELDO_BASICO", String.format("%.2f", remun));
            params.put("ASIGNACION_FAMILIAR", "0.00");
            params.put("GRATIFICACION_ORDINARIA", "0.00");
            params.put("PROMEDIO_HORAS_EXTRAS", "0.00");
            params.put("PROMEDIO_COMISIONES", "0.00");
            params.put("TOTAL_REMUNERACION", "S/ " + String.format("%.2f", remun));
            
            params.put("DIAS_COMPUTABLES", "180"); // Semestre completo
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
