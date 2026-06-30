package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.CandidatoAdicionalDto;
import com.indeci.rrhh.dto.GenerarPlanillaCabeceraDto;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanillaLoteService {

    private final PeriodoPlanillaRepository periodoPlanillaRepository;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final MovimientoPlanillaRepository movimientoPlanillaRepository;
    private final EmpleadoPuestoRepository empleadoPuestoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final GeneradorPlanillaService generadorPlanillaService;
    private final PlanillaLoteRepository planillaLoteRepository;

    @Transactional(readOnly = true)
    public List<CandidatoAdicionalDto> obtenerCandidatosAdicionales(String periodo) {
        PeriodoPlanilla periodoObj = periodoPlanillaRepository.findByPeriodoAndActivo(periodo, 1)
                .orElseThrow(() -> new NegocioException("Periodo no encontrado o inactivo"));

        // Mes comercial asumido de 30 días o usando inicio-fin del periodo.
        LocalDate inicioPeriodo = getInicioPeriodo(periodo);
        LocalDate finPeriodo = getFinPeriodo(periodo);

        List<CandidatoAdicionalDto> candidatos = new ArrayList<>();
        
        // 1. Obtener todos los empleados activos
        List<EmpleadoPlanilla> empleadosActivos = empleadoPlanillaRepository.findByActivo(1);
        
        // 2. Obtener movimientos del periodo
        List<MovimientoPlanilla> movimientos = movimientoPlanillaRepository.findByPeriodoAndActivo(periodo, 1);
        List<Long> empleadosConMovimiento = movimientos.stream().map(MovimientoPlanilla::getEmpleadoId).toList();

        for (EmpleadoPlanilla ep : empleadosActivos) {
            boolean tieneMovimiento = empleadosConMovimiento.contains(ep.getEmpleadoId());
            
            // Caso 1: Nuevo Ingreso (Rezagado)
            if (!tieneMovimiento && ep.getFechaInicioContrato() != null) {
                if (!ep.getFechaInicioContrato().isAfter(finPeriodo) && !ep.getFechaInicioContrato().isBefore(inicioPeriodo)) {
                    candidatos.add(mapToDto(ep, "NUEVO_INGRESO"));
                    continue; // Ya es candidato, pasar al siguiente
                }
            }

            // Caso 2: Cambio de Rol (Prorrateo)
            // Obtener el histórico de puestos del empleado
            List<EmpleadoPuesto> puestos = empleadoPuestoRepository.findByEmpleadoIdOrderByFechaInicioDesc(ep.getEmpleadoId());
            long puestosEnPeriodo = puestos.stream().filter(p -> p.getActivo() == 1 && seIntersectan(p.getFechaInicio(), p.getFechaFin(), inicioPeriodo, finPeriodo)).count();
            
            if (puestosEnPeriodo > 1) {
                // Hubo más de un puesto activo en este periodo (cambio de rol en el mismo mes)
                candidatos.add(mapToDto(ep, "CAMBIO_ROL"));
            }
        }

        return candidatos;
    }

    @Transactional
    public void generarLoteAdicional(GenerarPlanillaCabeceraDto request) {
        String periodo = request.getPeriodo();
        PeriodoPlanilla periodoObj = periodoPlanillaRepository.findByPeriodoAndActivo(periodo, 1)
                .orElseThrow(() -> new NegocioException("Periodo no encontrado o inactivo"));

        LocalDate inicioPeriodo = getInicioPeriodo(periodo);
        LocalDate finPeriodo = getFinPeriodo(periodo);
        
        List<MovimientoPlanilla> todosMovimientos = movimientoPlanillaRepository.findByPeriodoAndActivo(periodo, 1);
        List<EmpleadoPlanilla> todasPlanillas = empleadoPlanillaRepository.findByActivo(1);

        // Agrupar empleados solicitados por Régimen Laboral
        Map<String, List<Long>> empleadosPorRegimen = request.getEmpleadosIds().stream()
                .collect(Collectors.groupingBy(empId -> {
                    return todasPlanillas.stream()
                            .filter(ep -> ep.getEmpleadoId().equals(empId))
                            .findFirst()
                            .map(ep -> ep.getRegimenLaboral() != null ? ep.getRegimenLaboral().getCodigo() : "DESC")
                            .orElse("DESC");
                }));

        for (Map.Entry<String, List<Long>> entry : empleadosPorRegimen.entrySet()) {
            String regimen = entry.getKey();
            List<Long> empleadosRegimen = entry.getValue();

            // Calcular Correlativo Dinámico para este Régimen
            Integer maxCorrelativo = planillaLoteRepository.findMaxCorrelativo(periodo, regimen, "ADICIONAL");
            int nextLoteNum = (maxCorrelativo == null) ? 1 : maxCorrelativo + 1;

            // Crear PlanillaLote
            PlanillaLote lote = new PlanillaLote();
            lote.setPeriodo(periodo);
            lote.setRegimenLaboralCodigo(regimen);
            lote.setTipoPlanilla("ADICIONAL");
            lote.setConceptoPlanilla(request.getConcepto());
            lote.setCorrelativo(nextLoteNum);
            lote.setEstado("GENERADO");
            planillaLoteRepository.save(lote);

            String tipoPlanillaAdicional = "ADICIONAL_" + nextLoteNum;

            for (Long empleadoId : empleadosRegimen) {
                log.info("Generando planilla adicional para empleado {} en periodo {} regimen {}", empleadoId, periodo, regimen);

                List<MovimientoPlanilla> movimientosDelEmpleado = todosMovimientos.stream()
                        .filter(m -> m.getEmpleadoId().equals(empleadoId))
                        .toList();

                List<EmpleadoPuesto> puestos = empleadoPuestoRepository.findByEmpleadoIdOrderByFechaInicioDesc(empleadoId);
                List<EmpleadoPuesto> puestosDelMes = puestos.stream()
                        .filter(p -> p.getActivo() == 1 && seIntersectan(p.getFechaInicio(), p.getFechaFin(), inicioPeriodo, finPeriodo))
                        .toList();
                
                List<EmpleadoPlanilla> planillas = empleadoPlanillaRepository.findByEmpleadoId(empleadoId);

                for (EmpleadoPuesto puesto : puestosDelMes) {
                    boolean yaPagado = movimientosDelEmpleado.stream()
                            .anyMatch(m -> m.getEmpleadoPuestoId() != null && m.getEmpleadoPuestoId().equals(puesto.getId()));
                    
                    if (yaPagado) {
                        log.info("El puesto {} ya fue liquidado en este periodo, omitiendo...", puesto.getId());
                        continue;
                    }

                    LocalDate inicioReal = (puesto.getFechaInicio() != null && puesto.getFechaInicio().isAfter(inicioPeriodo)) ? puesto.getFechaInicio() : inicioPeriodo;
                    LocalDate finReal = (puesto.getFechaFin() != null && puesto.getFechaFin().isBefore(finPeriodo)) ? puesto.getFechaFin() : finPeriodo;
                    
                    long diasEnRol = calcularDiasEnMes(puesto.getFechaInicio(), puesto.getFechaFin(), inicioPeriodo, finPeriodo);
                    
                    EmpleadoPlanilla planillaRol = planillas.stream()
                            .filter(ep -> seIntersectan(ep.getFechaInicio(), ep.getFechaFin(), puesto.getFechaInicio(), puesto.getFechaFin()))
                            .findFirst()
                            .orElse(planillas.stream().filter(ep -> ep.getActivo() == 1).findFirst().orElse(null));
                            
                    java.math.BigDecimal sueldoBasico = planillaRol != null && planillaRol.getSueldoBasico() != null 
                            ? java.math.BigDecimal.valueOf(planillaRol.getSueldoBasico()) 
                            : java.math.BigDecimal.ZERO;
                            
                    java.math.BigDecimal divisor = new java.math.BigDecimal("30");
                    java.math.BigDecimal sueldoProrrateado = sueldoBasico.divide(divisor, 6, java.math.RoundingMode.HALF_UP)
                            .multiply(java.math.BigDecimal.valueOf(diasEnRol)).setScale(2, java.math.RoundingMode.HALF_UP);

                    log.info("Sueldo prorrateado para empleado {} (puesto {}): {}", empleadoId, puesto.getId(), sueldoProrrateado);
                    
                    generadorPlanillaService.generarConOverride(
                            empleadoId, periodo, sueldoProrrateado, puesto.getId(), tipoPlanillaAdicional, inicioReal, finReal, lote.getId());
                }
            }
        }
    }

    private long calcularDiasEnMes(LocalDate inicioPuesto, LocalDate finPuesto, LocalDate inicioMes, LocalDate finMes) {
        LocalDate inicioReal = (inicioPuesto != null && inicioPuesto.isAfter(inicioMes)) ? inicioPuesto : inicioMes;
        LocalDate finReal = (finPuesto != null && finPuesto.isBefore(finMes)) ? finPuesto : finMes;
        
        // Matemática Comercial Inversa (Mes de 30 días)
        // Calculamos los días inactivos/no laborados para restarlos de 30.
        
        int faltantesInicio = 0;
        if (inicioReal.isAfter(inicioMes)) {
            faltantesInicio = Math.min(30, inicioReal.getDayOfMonth()) - 1;
        }
        
        int faltantesFin = 0;
        if (finReal.isBefore(finMes)) {
            int diaFin = Math.min(30, finReal.getDayOfMonth());
            faltantesFin = 30 - diaFin;
        }
        
        long diasComerciales = 30 - faltantesInicio - faltantesFin;
        return diasComerciales > 0 ? diasComerciales : 0;
    }

    private CandidatoAdicionalDto mapToDto(EmpleadoPlanilla ep, String motivo) {
        CandidatoAdicionalDto dto = new CandidatoAdicionalDto();
        dto.setEmpleadoId(ep.getEmpleadoId());
        dto.setRegimenLaboral(ep.getRegimenLaboral() != null ? ep.getRegimenLaboral().getCodigo() : "DESC");
        dto.setFechaIngreso(ep.getFechaInicioContrato());
        dto.setMotivo(motivo);
        
        Optional<Empleado> empOpt = empleadoRepository.findById(ep.getEmpleadoId());
        if (empOpt.isPresent() && empOpt.get().getPersona() != null) {
            dto.setDni(empOpt.get().getPersona().getDni());
            dto.setNombre(empOpt.get().getPersona().getNombreCompleto());
        } else {
            dto.setDni("N/A");
            dto.setNombre("Desconocido");
        }
        return dto;
    }

    private boolean seIntersectan(LocalDate inicio1, LocalDate fin1, LocalDate inicio2, LocalDate fin2) {
        if (inicio1 == null) return false;
        LocalDate f1 = fin1 != null ? fin1 : LocalDate.of(9999, 12, 31);
        LocalDate f2 = fin2 != null ? fin2 : LocalDate.of(9999, 12, 31);
        return !inicio1.isAfter(f2) && !f1.isBefore(inicio2);
    }
    
    private LocalDate getInicioPeriodo(String periodo) {
        String p = periodo == null ? "" : periodo.replace("-", "").trim();
        if (p.length() < 6) throw new NegocioException("Periodo inválido: " + periodo);
        int year = Integer.parseInt(p.substring(0, 4));
        int month = Integer.parseInt(p.substring(4, 6));
        return LocalDate.of(year, month, 1);
    }
    
    private LocalDate getFinPeriodo(String periodo) {
        LocalDate inicio = getInicioPeriodo(periodo);
        return YearMonth.from(inicio).atEndOfMonth();
    }
}
