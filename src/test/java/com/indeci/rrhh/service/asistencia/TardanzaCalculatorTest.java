package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.JornadaRegimen;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TardanzaCalculatorTest {

    private JornadaRegimen jornada(String ingreso, String refrFin, int tolIngreso, int tolAlmuerzo) {
        JornadaRegimen j = new JornadaRegimen();
        j.setHoraIngreso(ingreso);
        j.setRefrigerioFin(refrFin);
        j.setToleranciaIngresoMin(tolIngreso);
        j.setToleranciaAlmuerzoMin(tolAlmuerzo);
        return j;
    }

    private MarcadorCsvRow fila(String marca1, String marca3) {
        MarcadorCsvRow row = new MarcadorCsvRow();
        row.setMarca1(marca1);
        row.setMarca3(marca3);
        return row;
    }

    @Test
    void ingreso_conTardanza_descuentaTolerancia() {
        // 08:10 vs 08:00, tolerancia 5 → MAX(0, 10-5) = 5
        Integer min = TardanzaCalculator.calcular(fila("08:10", null), jornada("08:00", "14:00", 5, 5));
        assertThat(min).isEqualTo(5);
    }

    @Test
    void ingreso_dentroDeTolerancia_esCero() {
        // 08:04 vs 08:00, tolerancia 5 → 0
        Integer min = TardanzaCalculator.calcular(fila("08:04", null), jornada("08:00", "14:00", 5, 5));
        assertThat(min).isZero();
    }

    @Test
    void ingreso_masRegresoAlmuerzo_suma() {
        // Ingreso: 08:10 vs 08:00 tol 5 → 5 ; Regreso: 14:08 vs 14:00 tol 5 → 3 ; total 8
        Integer min = TardanzaCalculator.calcular(fila("08:10", "14:08"), jornada("08:00", "14:00", 5, 5));
        assertThat(min).isEqualTo(8);
    }

    @Test
    void sinJornada_devuelveNull() {
        assertThat(TardanzaCalculator.calcular(fila("08:10", "14:08"), null)).isNull();
    }

    @Test
    void sinMarcasComparables_devuelveNull() {
        // Sin Marca1 ni Marca3 no hay nada que comparar → null (fallback al reloj)
        assertThat(TardanzaCalculator.calcular(fila(null, null), jornada("08:00", "14:00", 5, 5))).isNull();
    }
}
