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
}
