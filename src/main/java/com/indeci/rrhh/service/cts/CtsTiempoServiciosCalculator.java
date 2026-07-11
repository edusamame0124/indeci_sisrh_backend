package com.indeci.rrhh.service.cts;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

/**
 * Feature 016 — Tiempo de servicios computable (años/meses/días efectivos) entre
 * la fecha de ingreso del vínculo y su fecha de cese, INCLUSIVE.
 *
 * <p>Convención /30 del sector público: la fracción sub-anual se expresa en días
 * efectivos = meses×30 + días, para la fórmula {@code (base×factor/360)×díasFracción}.
 * Puro y determinístico (sin dependencias) → testeable de forma aislada.</p>
 */
@Component
public class CtsTiempoServiciosCalculator {

    private static final int DIAS_POR_MES = 30;

    public record TiempoServicios(int anios, int meses, int dias, long diasFraccion) {}

    public TiempoServicios computar(LocalDate ingreso, LocalDate cese) {
        if (ingreso == null || cese == null || cese.isBefore(ingreso)) {
            return new TiempoServicios(0, 0, 0, 0);
        }
        // Cese inclusive: se cuenta el propio día de cese como laborado.
        Period p = Period.between(ingreso, cese.plusDays(1));
        int anios = p.getYears();
        int meses = p.getMonths();
        int dias = p.getDays();
        long diasFraccion = (long) meses * DIAS_POR_MES + dias;
        return new TiempoServicios(anios, meses, dias, diasFraccion);
    }

    /**
     * SPEC_VACACIONES F9.1 / CTS — Resta días NO computables (LSG + faltas injustificadas,
     * Art. 8 TUO Ley de CTS) de un tiempo de servicios ya calculado, re-derivando años/meses/días
     * en base 30/360 (puede "pedir prestado" un mes o un año completo si el descuento lo cruza).
     *
     * <p>Puro y determinístico (sin dependencias): recibe el total ya calculado y el descuento ya
     * resuelto por el caller — mantiene la calculadora testeable de forma aislada.</p>
     */
    public TiempoServicios descontar(TiempoServicios ideal, int diasNoComputables) {
        long totalIdeal = (long) ideal.anios() * 360 + ideal.diasFraccion();
        long totalReal = Math.max(0, totalIdeal - Math.max(0, diasNoComputables));
        int anios = (int) (totalReal / 360);
        long restoFraccion = totalReal - (long) anios * 360;
        int meses = (int) (restoFraccion / DIAS_POR_MES);
        int dias = (int) (restoFraccion - (long) meses * DIAS_POR_MES);
        return new TiempoServicios(anios, meses, dias, restoFraccion);
    }
}
