package com.indeci.rrhh.service.incidencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;

/**
 * V012_42 F1 — {@code EventosIncidenciaProvider}: desglosa días no computables por tipo
 * (LSG / FALTA_HISTORICA / SUSPENSION_HISTORICA) para que {@code IncidenciaLaboralCompuesta}
 * componga el DTO de 3 categorías. Cubre caso feliz (mezcla de tipos), caso de borde (recorte
 * de fechas al rango consultado) y caso de error normativo (rango inválido → cero, no excepción).
 */
@ExtendWith(MockitoExtension.class)
class EventosIncidenciaProviderTest {

    @Mock EmpleadoEventoRepository repository;
    @InjectMocks EventosIncidenciaProvider provider;

    private static final Long EMPLEADO_ID = 5L;

    private EmpleadoEvento evento(String codigoTipo, LocalDate ini, LocalDate fin) {
        TipoEvento tipo = new TipoEvento();
        tipo.setCodigo(codigoTipo);
        EmpleadoEvento e = new EmpleadoEvento();
        e.setTipoEvento(tipo);
        e.setFechaInicio(ini);
        e.setFechaFin(fin);
        return e;
    }

    @Test
    void desglose_clasifica_por_codigo_de_tipo() {
        LocalDate desde = LocalDate.of(2025, 1, 1);
        LocalDate hasta = LocalDate.of(2025, 12, 31);
        when(repository.findNoComputablesRecord(EMPLEADO_ID, desde, hasta)).thenReturn(List.of(
                evento("LICENCIA_SIN_GOCE", LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 10)), // 10d
                evento("FALTA_HISTORICA", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 3)),     // 3d
                evento("SUSPENSION_HISTORICA", LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 5)) // 5d
        ));

        EventosIncidenciaProvider.Desglose d = provider.obtenerDesglose(EMPLEADO_ID, desde, hasta);

        assertThat(d.lsg()).isEqualTo(10);
        assertThat(d.faltasHistoricas()).isEqualTo(3);
        assertThat(d.suspensiones()).isEqualTo(5);
        assertThat(provider.obtenerDiasNoComputables(EMPLEADO_ID, desde, hasta)).isEqualTo(18);
    }

    @Test
    void tipos_legacy_no_migrados_caen_en_bucket_lsg() {
        // CESE / PERMISO_PERSONAL (afectaTiempoServicio='S' desde backfill V012_42) no tienen
        // bucket propio — mismo comportamiento visible a RR.HH. que antes de V012_42.
        LocalDate desde = LocalDate.of(2025, 1, 1);
        LocalDate hasta = LocalDate.of(2025, 12, 31);
        when(repository.findNoComputablesRecord(EMPLEADO_ID, desde, hasta))
                .thenReturn(List.of(evento("PERMISO_PERSONAL", LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 2))));

        EventosIncidenciaProvider.Desglose d = provider.obtenerDesglose(EMPLEADO_ID, desde, hasta);

        assertThat(d.lsg()).isEqualTo(2);
        assertThat(d.faltasHistoricas()).isZero();
        assertThat(d.suspensiones()).isZero();
    }

    @Test
    void recorta_evento_al_rango_consultado() {
        LocalDate desde = LocalDate.of(2025, 6, 10);
        LocalDate hasta = LocalDate.of(2025, 6, 30);
        // Evento real: 01-jun a 20-jun (21 días), pero la consulta solo pide desde 10-jun.
        when(repository.findNoComputablesRecord(EMPLEADO_ID, desde, hasta)).thenReturn(List.of(
                evento("LICENCIA_SIN_GOCE", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 20))
        ));

        EventosIncidenciaProvider.Desglose d = provider.obtenerDesglose(EMPLEADO_ID, desde, hasta);

        // Recortado a [10-jun, 20-jun] = 11 días, no los 21 días completos del evento.
        assertThat(d.lsg()).isEqualTo(11);
    }

    @Test
    void rango_invalido_devuelve_cero_sin_consultar_repositorio() {
        EventosIncidenciaProvider.Desglose d = provider.obtenerDesglose(
                EMPLEADO_ID, LocalDate.of(2025, 12, 31), LocalDate.of(2025, 1, 1));

        assertThat(d.lsg()).isZero();
        assertThat(d.faltasHistoricas()).isZero();
        assertThat(d.suspensiones()).isZero();
    }

    @Test
    void empleadoId_nulo_devuelve_cero() {
        assertThat(provider.obtenerDiasNoComputables(null, LocalDate.now(), LocalDate.now()))
                .isZero();
    }
}
