package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * Resultado del cálculo vacacional — SPEC_VACACIONES F3. Reproduce las columnas
 * P/R/T/U/V/W de la MATRIZ_VACACIONES del especialista + estado de récord (D5).
 *
 * @param diasCorresponden días que corresponden = años completos × 30 (col P)
 * @param diasGozados       días gozados (col Q)
 * @param saldo             saldo pendiente = corresponden − gozados (col R; puede ser negativo)
 * @param costoNoGozadas    rem/30 × saldo (col T)
 * @param costoTruncasMes   rem/12 × meses (col U)
 * @param costoTruncasDia   rem/360 × días (col V)
 * @param costoTotal        T + U + V (col W)
 * @param diasEfectivos     días 30/360 − no computables (base del récord)
 * @param umbralRecord      210 (jornada 5d) o 260 (jornada 6d) — D.Leg. 1405 art. 2.2
 * @param estadoRecord      OK | SIN_RECORD_LEGAL
 */
public record VacacionCalculoDto(
        int diasCorresponden,
        double diasGozados,
        double saldo,
        BigDecimal costoNoGozadas,
        BigDecimal costoTruncasMes,
        BigDecimal costoTruncasDia,
        BigDecimal costoTotal,
        int diasEfectivos,
        int umbralRecord,
        String estadoRecord) {

    public static final String RECORD_OK = "OK";
    public static final String RECORD_SIN = "SIN_RECORD_LEGAL";
}
