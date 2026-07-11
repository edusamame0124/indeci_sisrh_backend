package com.indeci.rrhh.service.incidencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;

/**
 * SPEC_VACACIONES F9.1 — providers de días no computables al récord.
 */
@ExtendWith(MockitoExtension.class)
class IncidenciaProvidersTest {

    @Mock private EmpleadoEventoRepository empleadoEventoRepository;
    @Mock private AsistenciaDetalleRepository asistenciaDetalleRepository;

    private static final Long EMP = 42L;
    private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 1, 31);

    private EmpleadoEvento evento(LocalDate ini, LocalDate fin) {
        EmpleadoEvento e = new EmpleadoEvento();
        e.setFechaInicio(ini);
        e.setFechaFin(fin);
        return e;
    }

    @Test
    void eventos_cuenta_dias_dentro_del_rango_inclusive() {
        // Evento 10-ene a 20-ene → 11 días (ambos extremos).
        when(empleadoEventoRepository.findNoComputablesRecord(EMP, DESDE, HASTA))
                .thenReturn(List.of(evento(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20))));

        int dias = new EventosIncidenciaProvider(empleadoEventoRepository)
                .obtenerDiasNoComputables(EMP, DESDE, HASTA);

        assertThat(dias).isEqualTo(11);
    }

    @Test
    void eventos_recorta_al_rango_para_no_sobrecontar() {
        // Evento 25-dic-2025 a 10-ene-2026; solo 1..10 ene caen en el rango → 10 días.
        when(empleadoEventoRepository.findNoComputablesRecord(EMP, DESDE, HASTA))
                .thenReturn(List.of(evento(LocalDate.of(2025, 12, 25), LocalDate.of(2026, 1, 10))));

        int dias = new EventosIncidenciaProvider(empleadoEventoRepository)
                .obtenerDiasNoComputables(EMP, DESDE, HASTA);

        assertThat(dias).isEqualTo(10);
    }

    @Test
    void inasistencias_delega_el_conteo_de_faltas() {
        when(asistenciaDetalleRepository.contarFaltas(EMP, DESDE, HASTA)).thenReturn(7L);

        int dias = new InasistenciasIncidenciaProvider(asistenciaDetalleRepository)
                .obtenerDiasNoComputables(EMP, DESDE, HASTA);

        assertThat(dias).isEqualTo(7);
    }

    @Test
    void compuesta_suma_eventos_mas_inasistencias() {
        when(empleadoEventoRepository.findNoComputablesRecord(any(), any(), any()))
                .thenReturn(List.of(evento(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20)))); // 11
        when(asistenciaDetalleRepository.contarFaltas(any(), any(), any())).thenReturn(4L);        // 4

        IncidenciaLaboralCompuesta compuesta = new IncidenciaLaboralCompuesta(
                new EventosIncidenciaProvider(empleadoEventoRepository),
                new InasistenciasIncidenciaProvider(asistenciaDetalleRepository));

        assertThat(compuesta.obtenerDiasNoComputables(EMP, DESDE, HASTA)).isEqualTo(15); // 11 + 4
    }

    @Test
    void rango_invalido_devuelve_cero() {
        int dias = new EventosIncidenciaProvider(empleadoEventoRepository)
                .obtenerDiasNoComputables(EMP, HASTA, DESDE); // desde > hasta
        assertThat(dias).isZero();
    }
}
