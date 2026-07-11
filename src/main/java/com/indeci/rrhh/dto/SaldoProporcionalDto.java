package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * Saldo vacacional PROPORCIONAL para el Adelanto de Vacaciones — Art. 10 del
 * D.S. 013-2019-PCM (reglamento del D.Leg. 1405). Aplica por igual a 276, CAS (1057)
 * y SERVIR (30057): por cada mes completo de servicio EFECTIVO el servidor acumula
 * 2.5 días (30/12). Los días de LSG + faltas injustificadas no computan.
 *
 * @param mesesEfectivos    meses completos efectivos del período de devengo en curso (netos de incidencias)
 * @param saldoProporcional días proporcionales devengados = mesesEfectivos × 2.5
 * @param diasAdelantados   días ya tomados como adelanto en el período en curso (no reincidibles)
 * @param saldoDisponible   tope real de adelanto = saldoProporcional − diasAdelantados (≥ 0)
 */
public record SaldoProporcionalDto(
        int mesesEfectivos,
        BigDecimal saldoProporcional,
        double diasAdelantados,
        double saldoDisponible) {
}
