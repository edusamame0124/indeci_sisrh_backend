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

    // ── descontar (SPEC_VACACIONES F9.1 / CTS Art. 8 TUO Ley de CTS) ──

    @Test
    void descontar_sin_incidencias_no_cambia_nada() {
        TiempoServicios ideal = new TiempoServicios(4, 5, 15, 165);
        TiempoServicios t = calc.descontar(ideal, 0);
        assertEquals(4, t.anios());
        assertEquals(5, t.meses());
        assertEquals(15, t.dias());
        assertEquals(165, t.diasFraccion());
    }

    @Test
    void descontar_resta_dentro_de_la_fraccion_sin_tocar_anios() {
        TiempoServicios ideal = new TiempoServicios(4, 5, 15, 165); // 4a 5m 15d
        TiempoServicios t = calc.descontar(ideal, 40); // 165-40=125 → 4m 5d
        assertEquals(4, t.anios());
        assertEquals(4, t.meses());
        assertEquals(5, t.dias());
        assertEquals(125, t.diasFraccion());
    }

    @Test
    void descontar_cruza_un_anio_completo_pide_prestado() {
        // Ejemplo del usuario: 6 meses ideal (180 fracción, 0 años) − 5 días LSG → 5m 25d.
        TiempoServicios ideal = new TiempoServicios(0, 6, 0, 180);
        TiempoServicios t = calc.descontar(ideal, 5);
        assertEquals(0, t.anios());
        assertEquals(5, t.meses());
        assertEquals(25, t.dias());
        assertEquals(175, t.diasFraccion());
    }

    @Test
    void descontar_exceso_no_baja_de_cero() {
        TiempoServicios ideal = new TiempoServicios(0, 1, 0, 30);
        TiempoServicios t = calc.descontar(ideal, 999);
        assertEquals(0, t.anios());
        assertEquals(0, t.meses());
        assertEquals(0, t.dias());
        assertEquals(0, t.diasFraccion());
    }
}
