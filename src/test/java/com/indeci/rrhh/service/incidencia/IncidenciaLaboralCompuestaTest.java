package com.indeci.rrhh.service.incidencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.dto.DiasNoComputablesDto;

/**
 * SPEC_VACACIONES F9.1 / V012_42 F1 — desglose de días no computables (LSG / faltas /
 * suspensiones) para trazabilidad RR.HH. "Faltas" combina InasistenciasIncidenciaProvider
 * (operativa) + FALTA_HISTORICA (histórico migrado, ya viene sumado dentro del Desglose de
 * EventosIncidenciaProvider).
 */
@ExtendWith(MockitoExtension.class)
class IncidenciaLaboralCompuestaTest {

    @Mock private EventosIncidenciaProvider eventos;
    @Mock private InasistenciasIncidenciaProvider inasistencias;
    @InjectMocks private IncidenciaLaboralCompuesta compuesta;

    private static final LocalDate DESDE = LocalDate.of(2025, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2025, 12, 31);

    @Test
    void desglose_separa_lsg_faltas_y_suspensiones() {
        when(eventos.obtenerDesglose(eq(1L), any(), any()))
                .thenReturn(new EventosIncidenciaProvider.Desglose(30, 4, 6));
        when(inasistencias.obtenerDiasNoComputables(eq(1L), any(), any())).thenReturn(5);

        DiasNoComputablesDto d = compuesta.calcularDesglose(1L, DESDE, HASTA);

        assertThat(d.lsg()).isEqualTo(30);
        assertThat(d.faltas()).isEqualTo(9); // 5 operativas + 4 FALTA_HISTORICA
        assertThat(d.suspensiones()).isEqualTo(6);
        assertThat(d.total()).isEqualTo(45);
    }

    @Test
    void sin_incidencias_total_cero() {
        when(eventos.obtenerDesglose(eq(2L), any(), any()))
                .thenReturn(new EventosIncidenciaProvider.Desglose(0, 0, 0));
        when(inasistencias.obtenerDiasNoComputables(eq(2L), any(), any())).thenReturn(0);

        DiasNoComputablesDto d = compuesta.calcularDesglose(2L, DESDE, HASTA);

        assertThat(d.total()).isZero();
    }

    @Test
    void obtenerDiasNoComputables_suma_las_tres_fuentes() {
        when(eventos.obtenerDesglose(eq(3L), any(), any()))
                .thenReturn(new EventosIncidenciaProvider.Desglose(12, 0, 3));
        when(inasistencias.obtenerDiasNoComputables(eq(3L), any(), any())).thenReturn(8);

        assertThat(compuesta.obtenerDiasNoComputables(3L, DESDE, HASTA)).isEqualTo(23);
    }
}
