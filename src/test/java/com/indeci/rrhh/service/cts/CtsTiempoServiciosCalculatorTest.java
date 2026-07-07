package com.indeci.rrhh.service.cts;

import com.indeci.rrhh.service.cts.CtsTiempoServiciosCalculator.TiempoServicios;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CtsTiempoServiciosCalculatorTest {

    private final CtsTiempoServiciosCalculator calc = new CtsTiempoServiciosCalculator();

    @Test
    void computa_anios_meses_dias_con_cese_inclusive() {
        TiempoServicios t = calc.computar(LocalDate.of(2022, 1, 1), LocalDate.of(2026, 6, 15));
        assertEquals(4, t.anios());
        assertEquals(5, t.meses());
        assertEquals(15, t.dias());
        assertEquals(165, t.diasFraccion()); // 5*30 + 15
    }

    @Test
    void menos_de_un_anio_solo_fraccion() {
        TiempoServicios t = calc.computar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15));
        assertEquals(0, t.anios());
        assertEquals(165, t.diasFraccion());
    }

    @Test
    void cese_igual_ingreso_cuenta_un_dia() {
        TiempoServicios t = calc.computar(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15));
        assertEquals(0, t.anios());
        assertEquals(0, t.meses());
        assertEquals(1, t.dias());
        assertEquals(1, t.diasFraccion());
    }

    @Test
    void cese_anterior_al_ingreso_devuelve_cero() {
        TiempoServicios t = calc.computar(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 1, 1));
        assertEquals(0, t.anios());
        assertEquals(0, t.diasFraccion());
    }
}
