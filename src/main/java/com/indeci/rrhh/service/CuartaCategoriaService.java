package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.CalculoRentaRequestDto;
import com.indeci.rrhh.dto.CalculoRentaResponseDto;
import com.indeci.rrhh.dto.Suspension4taVigenteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class CuartaCategoriaService {

    private final Suspension4taService suspension4taService;
    private static final BigDecimal LIMITE_MENSUAL = new BigDecimal("1500.00");
    private static final BigDecimal TASA_RETENCION = new BigDecimal("0.08");

    @Transactional(readOnly = true)
    public CalculoRentaResponseDto calcular(CalculoRentaRequestDto request) {
        log.info("Calculando renta de 4ta categoría para empleado {} en periodo {}/{}",
                request.getEmpleadoId(), request.getMes(), request.getAnioFiscal());

        CalculoRentaResponseDto response = new CalculoRentaResponseDto();
        response.setCategoria("CUARTA");

        BigDecimal ingresosMes = request.getIngresosMesActual();
        if (ingresosMes == null) {
            ingresosMes = BigDecimal.ZERO;
        }

        // Si el ingreso mensual no supera S/ 1500, no hay retención
        if (ingresosMes.compareTo(LIMITE_MENSUAL) <= 0) {
            response.setRetencionCalculada(BigDecimal.ZERO);
            response.setObservacion("Ingresos mensuales (" + ingresosMes + ") no superan el límite de S/ " + LIMITE_MENSUAL);
            return response;
        }

        // Consultar suspensión vigente al último día del mes evaluado
        LocalDate fechaDevengue = YearMonth.of(request.getAnioFiscal(), request.getMes()).atEndOfMonth();
        Suspension4taVigenteDto vigencia = suspension4taService.consultarVigente(request.getEmpleadoId(), fechaDevengue);

        if (vigencia != null && vigencia.vigente()) {
            response.setRetencionCalculada(BigDecimal.ZERO);
            response.setObservacion("Cuenta con suspensión de 4ta categoría vigente (Constancia: " + vigencia.nroConstancia() + ")");
            return response;
        }

        // Aplica retención del 8%
        BigDecimal retencion = ingresosMes.multiply(TASA_RETENCION).setScale(2, RoundingMode.HALF_UP);
        response.setRetencionCalculada(retencion);
        
        if (vigencia != null && vigencia.existeVencida()) {
            response.setObservacion("Se aplica 8%. La constancia de suspensión se encuentra vencida.");
        } else {
            response.setObservacion("Se aplica 8% sobre ingresos.");
        }
        
        return response;
    }
}
