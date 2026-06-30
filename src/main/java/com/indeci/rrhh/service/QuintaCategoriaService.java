package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.CalculoRentaRequestDto;
import com.indeci.rrhh.dto.CalculoRentaResponseDto;
import com.indeci.rrhh.dto.EmpleadoOtrosIngresosDto;
import com.indeci.rrhh.dto.LiquidacionQuintaDTO;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuintaCategoriaService {

    private final ParametroRemunerativoService parametroRemunerativoService;
    private final OtrosIngresosService otrosIngresosService;
    private final EmpleadoRepository empleadoRepository;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final MovimientoPlanillaRepository movimientoPlanillaRepository;

    // Tramos según SUNAT
    private static final BigDecimal TRAMO_1_LIMITE = new BigDecimal("5");
    private static final BigDecimal TRAMO_1_TASA = new BigDecimal("0.08");

    private static final BigDecimal TRAMO_2_LIMITE = new BigDecimal("20");
    private static final BigDecimal TRAMO_2_TASA = new BigDecimal("0.14");

    private static final BigDecimal TRAMO_3_LIMITE = new BigDecimal("35");
    private static final BigDecimal TRAMO_3_TASA = new BigDecimal("0.17");

    private static final BigDecimal TRAMO_4_LIMITE = new BigDecimal("45");
    private static final BigDecimal TRAMO_4_TASA = new BigDecimal("0.20");

    private static final BigDecimal TRAMO_5_TASA = new BigDecimal("0.30");

    @Transactional(readOnly = true)
    public CalculoRentaResponseDto calcular(CalculoRentaRequestDto request) {
        log.info("Calculando renta de 5ta categoría para empleado {} en año fiscal {}",
                request.getEmpleadoId(), request.getAnioFiscal());

        CalculoRentaResponseDto response = new CalculoRentaResponseDto();
        response.setCategoria("QUINTA");

        BigDecimal valorUIT = parametroRemunerativoService.obtenerValor("UIT", request.getAnioFiscal(), null);
        BigDecimal factorDeduccion = parametroRemunerativoService.obtenerValor("IR5TA_FACTOR_DEDUCCION", request.getAnioFiscal(), null);
        BigDecimal deduccionBase = valorUIT.multiply(factorDeduccion);

        BigDecimal ingresosMes = request.getIngresosMesActual() != null ? request.getIngresosMesActual() : BigDecimal.ZERO;
        
        // Extraer fechaInicioContrato
        LocalDate fechaInicioContrato = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(request.getEmpleadoId(), 1)
                .map(EmpleadoPlanilla::getFechaInicioContrato)
                .orElse(null);
        int mesInicioContrato = (fechaInicioContrato != null && fechaInicioContrato.getYear() == request.getAnioFiscal()) 
                ? fechaInicioContrato.getMonthValue() : 1;

        // Sumar planillas cerradas reales
        BigDecimal percibidas = BigDecimal.ZERO;
        List<MovimientoPlanilla> movimientos = movimientoPlanillaRepository.findByEmpleadoIdAndActivo(request.getEmpleadoId(), 1);
        for (MovimientoPlanilla mov : movimientos) {
            if (mov.getPeriodo() != null && mov.getPeriodo().startsWith(request.getAnioFiscal() + "-")) {
                int movMes = Integer.parseInt(mov.getPeriodo().split("-")[1]);
                if (movMes >= mesInicioContrato && movMes < request.getMes() && 
                    ("CERRADO".equals(mov.getEstado()) || "APROBADO".equals(mov.getEstado()))) {
                    percibidas = percibidas.add(BigDecimal.valueOf(mov.getTotalIngresos() != null ? mov.getTotalIngresos() : 0.0));
                }
            }
        }

        // Sueldo Proyectado
        int mesesFaltantes = 12 - request.getMes() + 1;
        BigDecimal sueldoProyectado = ingresosMes.multiply(new BigDecimal(mesesFaltantes));

        // Gratificaciones Proyectadas
        int gratiRestantes = 0;
        if (request.getMes() <= 7 && mesInicioContrato <= 7) gratiRestantes += 1; // Julio
        if (request.getMes() <= 12 && mesInicioContrato <= 12) gratiRestantes += 1; // Diciembre
        BigDecimal gratificaciones = ingresosMes.multiply(new BigDecimal(gratiRestantes));

        // RBA Estimado = Percibidas + Sueldo Proyectado + Gratificaciones Proyectadas
        BigDecimal rbaBase = percibidas.add(sueldoProyectado).add(gratificaciones);

        // Sumar otros ingresos (Fase 2)
        EmpleadoOtrosIngresosDto otrosIngresos = otrosIngresosService.obtenerPorEmpleadoYAno(request.getEmpleadoId(), request.getAnioFiscal());
        BigDecimal montoOtrosIngresos = (otrosIngresos != null && otrosIngresos.getMontoIngresos() != null)
                ? otrosIngresos.getMontoIngresos() : BigDecimal.ZERO;

        BigDecimal rbaTotal = rbaBase.add(montoOtrosIngresos);

        // Deducción de 7 UIT
        BigDecimal rentaNeta = rbaTotal.subtract(deduccionBase);
        if (rentaNeta.compareTo(BigDecimal.ZERO) <= 0) {
            response.setRetencionCalculada(BigDecimal.ZERO);
            response.setObservacion("Renta neta (" + rentaNeta + ") no supera la deducción de " + factorDeduccion + " UIT");
            return response;
        }

        // Calcular IAP (Impuesto Anual Proyectado)
        BigDecimal impuestoAnual = calcularImpuestoPorTramos(rentaNeta, valorUIT);

        // Restar retenciones previas de otros empleadores
        BigDecimal retencionesPrevias = (otrosIngresos != null && otrosIngresos.getMontoRetenciones() != null)
                ? otrosIngresos.getMontoRetenciones() : BigDecimal.ZERO;
        
        BigDecimal impuestoAnualNeto = impuestoAnual.subtract(retencionesPrevias);
        if (impuestoAnualNeto.compareTo(BigDecimal.ZERO) < 0) {
            impuestoAnualNeto = BigDecimal.ZERO;
        }

        // Retención Mensual (Divisor simple: 12) - la norma indica distintos divisores por mes (12 en Ene-Mar, 9 en Abr, 8 en May-Jul, etc.)
        // Para simplificar la base algorítmica y cumplir con la arquitectura, dividimos por 12 asumiendo Enero.
        int divisor = calcularDivisorMensual(request.getMes());
        BigDecimal retencionMensual = impuestoAnualNeto.divide(new BigDecimal(divisor), 2, RoundingMode.HALF_UP);

        response.setRetencionCalculada(retencionMensual);
        response.setObservacion("IAP: " + impuestoAnualNeto + " (Otros Ingresos: " + montoOtrosIngresos + " - Deducción 7UIT: " + deduccionBase + ")");
        return response;
    }

    private BigDecimal calcularImpuestoPorTramos(BigDecimal rentaNeta, BigDecimal valorUIT) {
        BigDecimal impuestoTotal = BigDecimal.ZERO;
        BigDecimal rentaRestante = rentaNeta;

        // Tramo 1 (hasta 5 UIT)
        BigDecimal limite1 = TRAMO_1_LIMITE.multiply(valorUIT);
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = rentaRestante.min(limite1);
            impuestoTotal = impuestoTotal.add(baseTramo.multiply(TRAMO_1_TASA));
            rentaRestante = rentaRestante.subtract(baseTramo);
        }

        // Tramo 2 (hasta 20 UIT)
        BigDecimal limite2 = TRAMO_2_LIMITE.multiply(valorUIT).subtract(limite1);
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = rentaRestante.min(limite2);
            impuestoTotal = impuestoTotal.add(baseTramo.multiply(TRAMO_2_TASA));
            rentaRestante = rentaRestante.subtract(baseTramo);
        }

        // Tramo 3 (hasta 35 UIT)
        BigDecimal limite3 = TRAMO_3_LIMITE.multiply(valorUIT).subtract(TRAMO_2_LIMITE.multiply(valorUIT));
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = rentaRestante.min(limite3);
            impuestoTotal = impuestoTotal.add(baseTramo.multiply(TRAMO_3_TASA));
            rentaRestante = rentaRestante.subtract(baseTramo);
        }

        // Tramo 4 (hasta 45 UIT)
        BigDecimal limite4 = TRAMO_4_LIMITE.multiply(valorUIT).subtract(TRAMO_3_LIMITE.multiply(valorUIT));
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = rentaRestante.min(limite4);
            impuestoTotal = impuestoTotal.add(baseTramo.multiply(TRAMO_4_TASA));
            rentaRestante = rentaRestante.subtract(baseTramo);
        }

        // Tramo 5 (más de 45 UIT)
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            impuestoTotal = impuestoTotal.add(rentaRestante.multiply(TRAMO_5_TASA));
        }

        return impuestoTotal.setScale(2, RoundingMode.HALF_UP);
    }

    private int calcularDivisorMensual(int mes) {
        if (mes <= 3) return 12; // Ene, Feb, Mar
        if (mes == 4) return 9;
        if (mes <= 7) return 8; // May, Jun, Jul
        if (mes == 8) return 5;
        if (mes <= 11) return 4; // Sep, Oct, Nov
        return 1; // Dic
    }

    @Transactional(readOnly = true)
    public LiquidacionQuintaDTO generarLiquidacion(Long empleadoId, Integer anioFiscal, Integer mesFiscal) {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new com.indeci.exception.NegocioException("Empleado no encontrado"));
                
        LiquidacionQuintaDTO dto = new LiquidacionQuintaDTO();
        dto.setIdEmpleado(empleado.getCodigoInterno() != null ? empleado.getCodigoInterno() : empleado.getId().toString());
        dto.setNombreCompleto(empleado.getPersona() != null ? empleado.getPersona().getNombreCompleto() : "Desconocido");
        dto.setPeriodoCalculo(anioFiscal + "-" + String.format("%02d", mesFiscal));

        // Bloque 1 (RBA)
        log.info("Buscando empleadoPlanilla para empleadoId: {}", empleadoId);
        BigDecimal sueldoMensual = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(planilla -> {
                    log.info("Planilla encontrada. sueldoBasico: {}", planilla.getSueldoBasico());
                    return planilla.getSueldoBasico();
                })
                .map(BigDecimal::valueOf)
                .orElseGet(() -> {
                    log.info("No se encontró planilla activa o el sueldoBasico es nulo");
                    return BigDecimal.ZERO;
                });
        
        log.info("Sueldo mensual obtenido para el cálculo: {}", sueldoMensual);
        
        // Extraer fechaInicioContrato
        LocalDate fechaInicioContrato = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(EmpleadoPlanilla::getFechaInicioContrato)
                .orElse(null);
        int mesInicioContrato = (fechaInicioContrato != null && fechaInicioContrato.getYear() == anioFiscal) 
                ? fechaInicioContrato.getMonthValue() : 1;

        // Calcular sueldo mensual proyectado (sueldo * meses faltantes)
        // Meses faltantes = 12 - mesFiscal + 1 (incluyendo el mes actual)
        int mesesFaltantes = 12 - mesFiscal + 1;
        BigDecimal sueldoProyectado = sueldoMensual.multiply(new BigDecimal(mesesFaltantes));
        dto.setSueldoMensualProyectado(sueldoProyectado);

        // Gratificaciones proyectadas (Julio y Diciembre)
        int gratiRestantes = 0;
        if (mesFiscal <= 7 && mesInicioContrato <= 7) gratiRestantes += 1; // Julio
        if (mesFiscal <= 12 && mesInicioContrato <= 12) gratiRestantes += 1; // Diciembre
        BigDecimal gratificaciones = sueldoMensual.multiply(new BigDecimal(gratiRestantes));
        dto.setGratificacionesProyectadas(gratificaciones);

        // Remuneraciones percibidas (meses anteriores)
        BigDecimal percibidas = BigDecimal.ZERO;
        List<MovimientoPlanilla> movimientos = movimientoPlanillaRepository.findByEmpleadoIdAndActivo(empleadoId, 1);
        for (MovimientoPlanilla mov : movimientos) {
            if (mov.getPeriodo() != null && mov.getPeriodo().startsWith(anioFiscal + "-")) {
                int movMes = Integer.parseInt(mov.getPeriodo().split("-")[1]);
                if (movMes >= mesInicioContrato && movMes < mesFiscal && 
                    ("CERRADO".equals(mov.getEstado()) || "APROBADO".equals(mov.getEstado()))) {
                    percibidas = percibidas.add(BigDecimal.valueOf(mov.getTotalIngresos() != null ? mov.getTotalIngresos() : 0.0));
                }
            }
        }
        dto.setRemuneracionesPercibidasAnteriores(percibidas);

        // Otros empleadores
        EmpleadoOtrosIngresosDto otrosIngresos = otrosIngresosService.obtenerPorEmpleadoYAno(empleadoId, anioFiscal);
        BigDecimal ingresosOtros = (otrosIngresos != null && otrosIngresos.getMontoIngresos() != null)
                ? otrosIngresos.getMontoIngresos() : BigDecimal.ZERO;
        dto.setIngresosOtrosEmpleadores(ingresosOtros);

        // Total RBA
        BigDecimal totalRba = sueldoProyectado.add(gratificaciones).add(percibidas).add(ingresosOtros);
        dto.setTotalRentaBrutaAnual(totalRba);

        // Bloque 2 (Deducción)
        BigDecimal valorUIT = parametroRemunerativoService.obtenerValor("UIT", anioFiscal, null);
        BigDecimal factorDeduccion = parametroRemunerativoService.obtenerValor("IR5TA_FACTOR_DEDUCCION", anioFiscal, null);
        BigDecimal deduccionBase = valorUIT.multiply(factorDeduccion);
        dto.setMontoDeduccionUIT(deduccionBase);

        BigDecimal rentaNeta = totalRba.subtract(deduccionBase);
        if (rentaNeta.compareTo(BigDecimal.ZERO) < 0) rentaNeta = BigDecimal.ZERO;
        dto.setTotalRentaNetaImponible(rentaNeta);

        // Bloque 3 (Tramos)
        calcularYAsignarTramosLiquidacion(rentaNeta, valorUIT, dto);

        // Bloque 4 (Prorrateo)
        BigDecimal retencionesPrevias = (otrosIngresos != null && otrosIngresos.getMontoRetenciones() != null)
                ? otrosIngresos.getMontoRetenciones() : BigDecimal.ZERO;
        dto.setRetencionesAcumuladasExternas(retencionesPrevias);

        BigDecimal saldoRetenerAnual = dto.getImpuestoAnualProyectado().subtract(retencionesPrevias);
        if (saldoRetenerAnual.compareTo(BigDecimal.ZERO) < 0) saldoRetenerAnual = BigDecimal.ZERO;
        dto.setSaldoRetenerAnual(saldoRetenerAnual);

        int divisor = calcularDivisorMensual(mesFiscal);
        dto.setDivisorMes(new BigDecimal(divisor));

        BigDecimal retencionMensual = saldoRetenerAnual.divide(new BigDecimal(divisor), 2, RoundingMode.HALF_UP);
        dto.setMontoRetenerMes(retencionMensual);

        return dto;
    }

    private void calcularYAsignarTramosLiquidacion(BigDecimal rentaNeta, BigDecimal valorUIT, LiquidacionQuintaDTO dto) {
        BigDecimal impuestoTotal = BigDecimal.ZERO;
        BigDecimal rentaRestante = rentaNeta;

        dto.setTramo1(BigDecimal.ZERO);
        dto.setTramo2(BigDecimal.ZERO);
        dto.setTramo3(BigDecimal.ZERO);
        dto.setTramo4(BigDecimal.ZERO);
        dto.setTramo5(BigDecimal.ZERO);

        BigDecimal limite1 = TRAMO_1_LIMITE.multiply(valorUIT);
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = rentaRestante.min(limite1);
            BigDecimal imp = baseTramo.multiply(TRAMO_1_TASA);
            dto.setTramo1(imp.setScale(2, RoundingMode.HALF_UP));
            impuestoTotal = impuestoTotal.add(imp);
            rentaRestante = rentaRestante.subtract(baseTramo);
        }

        BigDecimal limite2 = TRAMO_2_LIMITE.multiply(valorUIT).subtract(limite1);
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = rentaRestante.min(limite2);
            BigDecimal imp = baseTramo.multiply(TRAMO_2_TASA);
            dto.setTramo2(imp.setScale(2, RoundingMode.HALF_UP));
            impuestoTotal = impuestoTotal.add(imp);
            rentaRestante = rentaRestante.subtract(baseTramo);
        }

        BigDecimal limite3 = TRAMO_3_LIMITE.multiply(valorUIT).subtract(TRAMO_2_LIMITE.multiply(valorUIT));
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = rentaRestante.min(limite3);
            BigDecimal imp = baseTramo.multiply(TRAMO_3_TASA);
            dto.setTramo3(imp.setScale(2, RoundingMode.HALF_UP));
            impuestoTotal = impuestoTotal.add(imp);
            rentaRestante = rentaRestante.subtract(baseTramo);
        }

        BigDecimal limite4 = TRAMO_4_LIMITE.multiply(valorUIT).subtract(TRAMO_3_LIMITE.multiply(valorUIT));
        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseTramo = rentaRestante.min(limite4);
            BigDecimal imp = baseTramo.multiply(TRAMO_4_TASA);
            dto.setTramo4(imp.setScale(2, RoundingMode.HALF_UP));
            impuestoTotal = impuestoTotal.add(imp);
            rentaRestante = rentaRestante.subtract(baseTramo);
        }

        if (rentaRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal imp = rentaRestante.multiply(TRAMO_5_TASA);
            dto.setTramo5(imp.setScale(2, RoundingMode.HALF_UP));
            impuestoTotal = impuestoTotal.add(imp);
        }

        dto.setImpuestoAnualProyectado(impuestoTotal.setScale(2, RoundingMode.HALF_UP));
    }
}
