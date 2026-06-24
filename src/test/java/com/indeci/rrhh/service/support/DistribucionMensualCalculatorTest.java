package com.indeci.rrhh.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.indeci.rrhh.dto.EventoDistribucionMesDto;

/**
 * Tests del desglose mensual de un descanso médico/maternidad por días naturales
 * contiguos. La duración legal se reparte en tramos que nunca cruzan el corte de
 * mes (necesario para imputar a cada período de planilla).
 */
class DistribucionMensualCalculatorTest {

    @Test
    void fecha_fin_es_inicio_mas_duracion_menos_uno() {
        // 90 días naturales desde el 1-ene-2026 (no bisiesto): 31+28+31 = 90 → 31-mar.
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(
                LocalDate.of(2026, 1, 1), 90);

        assertThat(fin).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void descanso_dentro_de_un_mes_genera_un_solo_tramo() {
        List<EventoDistribucionMesDto> tramos = DistribucionMensualCalculator.calcular(
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 20));

        assertThat(tramos).hasSize(1);
        EventoDistribucionMesDto t = tramos.get(0);
        assertThat(t.getPeriodo()).isEqualTo("202601");
        assertThat(t.getFechaDesde()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(t.getFechaHasta()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(t.getDiasSubsidio()).isEqualTo(6);
        assertThat(t.getEstadoTramo()).isEqualTo("PENDIENTE_IMPUTACION");
        assertThat(t.getAfectaDiasLaborados()).isEqualTo("S");
    }

    @Test
    void descanso_a_caballo_de_dos_meses_corta_en_el_fin_de_mes() {
        List<EventoDistribucionMesDto> tramos = DistribucionMensualCalculator.calcular(
                LocalDate.of(2026, 1, 20), LocalDate.of(2026, 2, 10));

        assertThat(tramos).hasSize(2);
        // Enero: del 20 al 31 = 12 días.
        assertThat(tramos.get(0).getPeriodo()).isEqualTo("202601");
        assertThat(tramos.get(0).getFechaHasta()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(tramos.get(0).getDiasSubsidio()).isEqualTo(12);
        // Febrero: del 1 al 10 = 10 días.
        assertThat(tramos.get(1).getPeriodo()).isEqualTo("202602");
        assertThat(tramos.get(1).getFechaDesde()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(tramos.get(1).getDiasSubsidio()).isEqualTo(10);
        assertThat(DistribucionMensualCalculator.sumarDias(tramos)).isEqualTo(22);
    }

    @Test
    void licencia_maternidad_98_dias_se_reparte_y_suma_98() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 98);

        List<EventoDistribucionMesDto> tramos =
                DistribucionMensualCalculator.calcular(inicio, fin);

        // ene(31) + feb(28) + mar(31) + abr(8) = 98, en 4 períodos.
        assertThat(tramos).hasSize(4);
        assertThat(tramos).extracting(EventoDistribucionMesDto::getPeriodo)
                .containsExactly("202601", "202602", "202603", "202604");
        assertThat(tramos).extracting(EventoDistribucionMesDto::getDiasSubsidio)
                .containsExactly(31, 28, 31, 8);
        assertThat(DistribucionMensualCalculator.sumarDias(tramos)).isEqualTo(98);
    }

    @Test
    void sumar_dias_tolera_tramos_con_dias_nulos() {
        EventoDistribucionMesDto conValor = new EventoDistribucionMesDto();
        conValor.setDiasSubsidio(5);
        EventoDistribucionMesDto sinValor = new EventoDistribucionMesDto();
        sinValor.setDiasSubsidio(null);

        assertThat(DistribucionMensualCalculator.sumarDias(List.of(conValor, sinValor)))
                .isEqualTo(5);
    }
}
