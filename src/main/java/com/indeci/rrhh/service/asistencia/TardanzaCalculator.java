package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.JornadaRegimen;

/**
 * F-B — Recalcula los minutos de tardanza de un día a partir de las marcas reales
 * y la jornada configurada por régimen (ingreso + regreso de almuerzo, restando
 * las tolerancias). Es independiente del valor "Tard." del reloj.
 *
 * <p>Convención de marcas del reporte: Marca1 = entrada de jornada,
 * Marca3 = regreso de refrigerio. Si no hay jornada configurada o no hay marcas
 * comparables, devuelve {@code null} → el llamador hace fallback al valor del reloj.</p>
 */
public final class TardanzaCalculator {

    private TardanzaCalculator() {}

    /**
     * @return minutos de tardanza recalculados, o {@code null} si no se puede determinar
     *         (sin jornada configurada o sin marcas/horas comparables).
     */
    public static Integer calcular(MarcadorCsvRow row, JornadaRegimen jornada) {
        return calcular(row.getMarca1(), row.getMarca3(), jornada);
    }

    /**
     * Recalcula la tardanza a partir de la marca de ingreso (Marca1) y la de regreso
     * de refrigerio (Marca3). Útil para recalcular desde el detalle ya almacenado.
     *
     * @return minutos de tardanza, o {@code null} si no se puede determinar.
     */
    public static Integer calcular(String marcaIngresoStr, String marcaRegresoStr, JornadaRegimen jornada) {
        if (jornada == null) {
            return null;
        }

        int total = 0;
        boolean calculado = false;

        // Tardanza de ingreso: Marca1 vs hora de ingreso, menos tolerancia de ingreso.
        Integer marcaIngreso = toMinutos(marcaIngresoStr);
        Integer horaIngreso = toMinutos(jornada.getHoraIngreso());
        if (marcaIngreso != null && horaIngreso != null) {
            total += Math.max(0, marcaIngreso - horaIngreso - tol(jornada.getToleranciaIngresoMin()));
            calculado = true;
        }

        // Tardanza de regreso de almuerzo: Marca3 vs fin de refrigerio, menos su tolerancia.
        Integer marcaRegreso = toMinutos(marcaRegresoStr);
        Integer finRefrigerio = toMinutos(jornada.getRefrigerioFin());
        if (marcaRegreso != null && finRefrigerio != null) {
            total += Math.max(0, marcaRegreso - finRefrigerio - tol(jornada.getToleranciaAlmuerzoMin()));
            calculado = true;
        }

        return calculado ? total : null;
    }

    /**
     * Modelo de dos niveles (V010_95): tardanza diaria EN BRUTO, sin restar
     * tolerancia (el umbral diario hace la clasificación posterior). Suma la
     * demora de ingreso (Marca1 vs hora ingreso) y la de regreso de refrigerio
     * (Marca3 vs fin de refrigerio). Devuelve {@code null} si no se puede medir.
     */
    public static Integer calcularBruto(String marcaIngresoStr, String marcaRegresoStr, JornadaRegimen jornada) {
        if (jornada == null) {
            return null;
        }
        int total = 0;
        boolean calculado = false;

        Integer marcaIngreso = toMinutos(marcaIngresoStr);
        Integer horaIngreso = toMinutos(jornada.getHoraIngreso());
        if (marcaIngreso != null && horaIngreso != null) {
            total += Math.max(0, marcaIngreso - horaIngreso);
            calculado = true;
        }

        Integer marcaRegreso = toMinutos(marcaRegresoStr);
        Integer finRefrigerio = toMinutos(jornada.getRefrigerioFin());
        if (marcaRegreso != null && finRefrigerio != null) {
            total += Math.max(0, marcaRegreso - finRefrigerio);
            calculado = true;
        }

        return calculado ? total : null;
    }

    private static int tol(Integer valor) {
        return valor != null ? Math.max(0, valor) : 0;
    }

    /** 'HH:mm' (o 'HH:mm:ss') → minutos desde medianoche; null si no parsea. */
    private static Integer toMinutos(String hora) {
        if (hora == null || hora.isBlank()) {
            return null;
        }
        String[] partes = hora.trim().split(":");
        if (partes.length < 2) {
            return null;
        }
        try {
            int h = Integer.parseInt(partes[0].trim());
            int m = Integer.parseInt(partes[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return null;
            }
            return h * 60 + m;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
