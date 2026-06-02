package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * F3.5 / Tab 1 — Evolución multi-período.
 *
 * <p>Lista los últimos {@code meses} períodos terminando en
 * {@code periodoBase}, cada uno con sus totales agregados.</p>
 */
public record ReporteEvolucionDto(
        String periodoBase,
        int meses,
        BigDecimal totalNetoAcumulado,
        BigDecimal promedioMensual,
        /** Variación % entre el período base y el primero del rango. */
        BigDecimal variacionPctRango,
        List<ReporteEvolucionItemDto> items) {

    public record ReporteEvolucionItemDto(
            String periodo,
            int conteoEmpleados,
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos,
            BigDecimal totalNeto,
            BigDecimal totalAporteEmpleador,
            int conteoNetoNoVa,
            /** Δ% del neto vs el período inmediatamente anterior. Null en el primero. */
            BigDecimal deltaPctNetoVsAnterior) {}
}
