package com.indeci.rrhh.service.asistencia;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Descuento de salida anticipada de DOS NIVELES — mismo contrato y misma matemática que
 * {@link TardanzaDescuentoCalculator} (modelo Excel institucional INDECI), aplicado a
 * {@code minutosSalidaAnticipada} en vez de {@code minutosTardanza}.
 *
 * <ul>
 *   <li><b>Descuento 1 (diario):</b> días con salida anticipada &gt; umbral diario →
 *       se descuenta el total de minutos de esos días.</li>
 *   <li><b>Descuento 2 (mensual):</b> días con salida anticipada ≤ umbral se acumulan;
 *       si la suma supera el tope mensual, se descuenta el exceso.</li>
 * </ul>
 *
 * <p>Se mantiene como calculadora propia (no se reutiliza {@code TardanzaDescuentoCalculator}
 * directamente) porque Tardanza y Salida anticipada son infracciones normativamente distintas
 * (decisión RR.HH. 2026-08-07): comparten la fórmula hoy, pero cada una necesita poder
 * evolucionar (tolerancias, tope) sin arrastrar a la otra.</p>
 *
 * <p>Función pura y estática: facilita tests directos sin Spring.</p>
 */
public final class SalidaAnticipadaDescuentoCalculator {

    private SalidaAnticipadaDescuentoCalculator() {}

    /**
     * @param salidasAnticipadasDiarias minutos de salida anticipada por día (en bruto;
     *     null/≤0 se ignoran).
     * @param remuneracion     remuneración base mensual.
     * @param jornadaHoras     horas/día (divisor); si null o ≤0 se usa 8.
     * @param umbralDiarioMin  umbral diario (default normativo 10).
     * @param topeMensualMin   tope mensual acumulado (default normativo 60).
     */
    public static Resultado calcular(
            List<Integer> salidasAnticipadasDiarias,
            double remuneracion,
            BigDecimal jornadaHoras,
            int umbralDiarioMin,
            int topeMensualMin) {

        int minDiaria = 0;
        int minMenorAcum = 0;
        if (salidasAnticipadasDiarias != null) {
            for (Integer t : salidasAnticipadasDiarias) {
                if (t == null || t <= 0) {
                    continue;
                }
                if (t > umbralDiarioMin) {
                    minDiaria += t;          // Descuento 1: día completo
                } else {
                    minMenorAcum += t;       // pozo mensual (Descuento 2)
                }
            }
        }
        int minExcesoMes = Math.max(0, minMenorAcum - Math.max(0, topeMensualMin));

        double descDiaria  = monto(remuneracion, jornadaHoras, minDiaria);
        double descMensual = monto(remuneracion, jornadaHoras, minExcesoMes);

        Resultado r = new Resultado();
        r.setMinSalidaAnticDiaria(minDiaria);
        r.setMinSalidaAnticMenorAcum(minMenorAcum);
        r.setMinSalidaAnticExcesoMes(minExcesoMes);
        r.setDescuentoDiaria(descDiaria);
        r.setDescuentoMensual(descMensual);
        r.setDescuentoTotal(round2(descDiaria + descMensual));
        return r;
    }

    /** Descuento (S/) de {@code minutos} = remun × minutos / (30 × jornadaHoras × 60). */
    public static double descuento(double remuneracion, BigDecimal jornadaHoras, int minutos) {
        return monto(remuneracion, jornadaHoras, minutos);
    }

    /**
     * Multiplica ANTES de dividir (una sola división con escala 2) para evitar
     * pérdida de precisión: remun × minutos / (30 × jornadaHoras × 60).
     */
    private static double monto(double remuneracion, BigDecimal jornadaHoras, int minutos) {
        if (remuneracion <= 0 || minutos <= 0) {
            return 0.0;
        }
        BigDecimal jh = (jornadaHoras != null && jornadaHoras.signum() > 0)
                ? jornadaHoras
                : BigDecimal.valueOf(8);
        BigDecimal divisor = BigDecimal.valueOf(30).multiply(jh).multiply(BigDecimal.valueOf(60));
        return BigDecimal.valueOf(remuneracion)
                .multiply(BigDecimal.valueOf(minutos))
                .divide(divisor, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @Data
    public static class Resultado {
        private int minSalidaAnticDiaria;
        private int minSalidaAnticMenorAcum;
        private int minSalidaAnticExcesoMes;
        private double descuentoDiaria;
        private double descuentoMensual;
        private double descuentoTotal;
    }
}
