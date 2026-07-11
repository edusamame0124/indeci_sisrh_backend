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
 * SPEC_VACACIONES F9.1 — desglose de días no computables (LSG vs faltas) para trazabilidad RR.HH.
 */
@ExtendWith(MockitoExtension.class)
class IncidenciaLaboralCompuestaTest {

    @Mock private EventosIncidenciaProvider eventos;
    @Mock private InasistenciasIncidenciaProvider inasistencias;
    @InjectMocks private IncidenciaLaboralCompuesta compuesta;

    private static final LocalDate DESDE = LocalDate.of(2025, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2025, 12, 31);

    @Test
    void desglose_separa_lsg_y_faltas() {
        when(eventos.obtenerDiasNoComputables(eq(1L), any(), any())).thenReturn(30);
        when(inasistencias.obtenerDiasNoComputables(eq(1L), any(), any())).thenReturn(5);

        DiasNoComputablesDto d = compuesta.calcularDesglose(1L, DESDE, HASTA);

        assertThat(d.lsg()).isEqualTo(30);
        assertThat(d.faltas()).isEqualTo(5);
        assertThat(d.total()).isEqualTo(35);
    }

    @Test
    void sin_incidencias_total_cero() {
        when(eventos.obtenerDiasNoComputables(eq(2L), any(), any())).thenReturn(0);
        when(inasistencias.obtenerDiasNoComputables(eq(2L), any(), any())).thenReturn(0);

        DiasNoComputablesDto d = compuesta.calcularDesglose(2L, DESDE, HASTA);

        assertThat(d.total()).isZero();
    }

    @Test
    void obtenerDiasNoComputables_suma_ambas_fuentes() {
        when(eventos.obtenerDiasNoComputables(eq(3L), any(), any())).thenReturn(12);
        when(inasistencias.obtenerDiasNoComputables(eq(3L), any(), any())).thenReturn(8);

        assertThat(compuesta.obtenerDiasNoComputables(3L, DESDE, HASTA)).isEqualTo(20);
    }
}
