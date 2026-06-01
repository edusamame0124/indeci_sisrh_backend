package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * F3.5 / Tab 3 — Top conceptos del período.
 *
 * <p>Lista los conceptos con mayor monto total sumado en
 * {@code MovimientoPlanillaDetalle} del período, ordenados desc. La UI usa
 * {@code limite} para restringir el universo (10, 20, 50).</p>
 */
public record ReporteTopConceptosDto(
        String periodo,
        int limite,
        BigDecimal totalIngresosPeriodo,
        List<ReporteTopConceptoItemDto> items) {

    public record ReporteTopConceptoItemDto(
            Long conceptoPlanillaId,
            String codigoMef,
            String nombre,
            /** REMUNERATIVO | NO_REMUNERATIVO | DESCUENTO | APORTE_TRABAJADOR | APORTE_EMPLEADOR */
            String tipoConcepto,
            int conteoEmpleados,
            BigDecimal montoTotal,
            /** % vs totalIngresosPeriodo (0..100). */
            BigDecimal porcentajeIngresos) {}
}
