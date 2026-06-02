package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * F3.5 / Tab 2 — Distribución por régimen laboral.
 *
 * <p>Agrega los movimientos del período por régimen laboral del empleado
 * (a través de {@code EmpleadoPlanilla}). Devuelve totales y % de cada
 * régimen sobre el total del período.</p>
 */
public record ReporteRegimenDto(
        String periodo,
        int totalEmpleados,
        BigDecimal totalNeto,
        List<ReporteRegimenItemDto> items) {

    public record ReporteRegimenItemDto(
            String regimenCodigo,
            String regimenNombre,
            int conteoEmpleados,
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos,
            BigDecimal totalNeto,
            BigDecimal netoPromedio,
            /** % del total neto del período (0..100). */
            BigDecimal porcentajeTotal) {}
}
