package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Insumos del cálculo vacacional — SPEC_VACACIONES F3.
 *
 * <p>Servicio puro: el orquestador (F4/F5 padrón) arma estos valores desde F1
 * (tiempo de servicio), el saldo (gozados), la remuneración y la jornada. Aquí NO
 * se accede a repositorios: se calcula solo con lo recibido (facilita regresión Excel).
 *
 * @param empleadoId         opcional, ID del empleado para evaluar récord con el provider
 * @param fechaDesde         opcional, inicio del período para evaluar récord
 * @param fechaHasta         opcional, fin del período para evaluar récord
 * @param anios              años de servicio (de F1)
 * @param meses              meses de servicio (de F1) — para truncas
 * @param dias               días de servicio (de F1) — para truncas
 * @param totalDias360        total días 30/360 (de F1) — para récord
 * @param diasGanadosHistoricos dias ganados segun tabla de saldos (F9/D5)
 * @param diasGozados        días ya gozados (col Q del Excel)
 * @param remuneracionMensual remuneración mensual (col S)
 * @param jornadaDiasSemana  5 o 6 días/semana (umbral de récord 210/260 — D.Leg. 1405 art. 2.2)
 * @param diasNoComputables  LSG + inasistencias injustificadas + suspensiones imperfectas (récord).
 *                           En F3 suele ser 0; F9 lo alimenta con datos reales.
 */
public record VacacionCalculoInput(
        Long empleadoId,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        int anios,
        int meses,
        int dias,
        int totalDias360,
        double diasGanadosHistoricos,
        double diasGozados,
        BigDecimal remuneracionMensual,
        int jornadaDiasSemana,
        int diasNoComputables) {
}
