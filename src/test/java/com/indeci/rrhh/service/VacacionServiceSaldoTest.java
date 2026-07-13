package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.SolicitudVacacionDet;
import com.indeci.rrhh.entity.Vacacion;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.SolicitudVacacionDetRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.repository.VacacionRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;

/**
 * Blindaje del saldo vacacional en la aprobación (D.Leg. 1405):
 * validación y descuento usan el consumo NETO (díasNuevos − díasOriginales "_ACTUAL"),
 * de modo que una reprogramación/fraccionamiento (neto 0) ni se rechaza por falso
 * "saldo insuficiente" ni se descuenta dos veces.
 */
@ExtendWith(MockitoExtension.class)
class VacacionServiceSaldoTest {

    @Mock VacacionRepository vacacionRepository;
    @Mock SolicitudRrhhRepository solicitudRepository;
    @Mock TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;
    @Mock EstadoSolicitudRepository estadoSolicitudRepository;
    @Mock VacacionSaldoRepository vacacionSaldoRepository;
    @Mock SolicitudVacacionDetRepository solicitudVacacionDetRepository;
    @Mock TiempoServicioService tiempoServicioService;
    @Mock com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta incidenciaLaboralProvider;

    @InjectMocks VacacionService service;

    private SolicitudRrhh solicitud(long id, long empleadoId) {
        SolicitudRrhh s = new SolicitudRrhh();
        s.setId(id);
        s.setEmpleadoId(empleadoId);
        return s;
    }

    private SolicitudVacacionDet det(String tipo, double dias, LocalDate ini) {
        SolicitudVacacionDet d = new SolicitudVacacionDet();
        d.setTipo(tipo);
        d.setTotalDias(dias);
        d.setFechaInicio(ini);
        d.setFechaFin(ini.plusDays((long) dias - 1));
        return d;
    }

    private VacacionSaldo saldo(long empleadoId, int anio, double ganados, double gozados) {
        VacacionSaldo v = new VacacionSaldo();
        v.setEmpleadoId(empleadoId);
        v.setAnio(anio);
        v.setDiasGanados(ganados);
        v.setDiasGozados(gozados);
        return v;
    }

    private Vacacion vacacionOrigen(long id, long empleadoId) {
        Vacacion v = new Vacacion();
        v.setId(id);
        v.setEmpleadoId(empleadoId);
        v.setPeriodoDesde(LocalDate.of(2026, 6, 1));
        v.setPeriodoHasta(LocalDate.of(2026, 6, 20));
        v.setDias(20d);
        v.setEstado("GOZADO");
        v.setActivo(1);
        return v;
    }

    // ── Reprogramación: neto 0 — NO debe rechazar (ni siquiera mira el saldo) ──
    @Test
    void reprogramacion_no_genera_falso_saldo_insuficiente() {
        SolicitudRrhh s = solicitud(500L, 100L);
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(500L, 1)).thenReturn(List.of(
                det("REPROG_ACTUAL", 20, LocalDate.of(2026, 6, 1)),
                det("REPROG_NUEVO", 20, LocalDate.of(2026, 8, 1))));

        // No lanza: neto = 20 − 20 = 0 ⇒ corta antes de consultar el saldo.
        service.validarSaldoAprobacion(s);

        verify(vacacionSaldoRepository, never()).findByEmpleadoIdAndActivoOrderByAnioAsc(anyLong(), anyInt());
    }

    // ── Reprogramación: el descuento es NETO 0 (no doble descuento) ──
    @Test
    void reprogramacion_descuenta_neto_cero_y_registra_solo_el_periodo_nuevo() {
        SolicitudRrhh s = solicitud(500L, 100L);
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(500L, 1)).thenReturn(List.of(
                det("REPROG_ACTUAL", 20, LocalDate.of(2026, 6, 1)),
                det("REPROG_NUEVO", 20, LocalDate.of(2026, 8, 1))));

        service.procesarAprobacionVacaciones(s, null);

        // Solo se registra el periodo de goce nuevo (el "_ACTUAL" no genera goce).
        verify(vacacionRepository, times(1)).save(any());
        // Neto 0 ⇒ el saldo no se toca.
        verify(vacacionSaldoRepository, never()).save(any());
        verify(vacacionSaldoRepository, never()).findByEmpleadoIdAndActivoOrderByAnioAsc(anyLong(), anyInt());
    }

    // ── Programación normal: valida contra el disponible ──
    @Test
    void programacion_insuficiente_lanza_negocio() {
        SolicitudRrhh s = solicitud(501L, 101L);
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(501L, 1))
                .thenReturn(List.of(det("PROGRAMACION", 20, LocalDate.of(2026, 8, 1))));
        when(vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(101L, 1))
                .thenReturn(List.of(saldo(101L, 2026, 10, 0)));

        assertThatThrownBy(() -> service.validarSaldoAprobacion(s))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("insuficiente");
    }

    // ── Adelanto: excede el saldo proporcional (mes efectivo × 2.5) — RECHAZA (anti-sobregiro) ──
    @Test
    void adelanto_excede_saldo_proporcional_lanza_negocio() {
        SolicitudRrhh s = solicitud(600L, 200L);
        // Servidor con 4 meses de servicio efectivo → 4 × 2.5 = 10 días proporcionales.
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(600L, 1))
                .thenReturn(List.of(det("ADELANTO", 15, LocalDate.of(2026, 5, 5))));
        when(tiempoServicioService.calcular(eq(200L), any()))
                .thenReturn(new com.indeci.rrhh.dto.TiempoServicioDto(
                        200L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1),
                        0, 4, 0, 120, 1, false));
        when(incidenciaLaboralProvider.obtenerDiasNoComputables(eq(200L), any(), any())).thenReturn(0);
        when(vacacionRepository.findByEmpleadoIdAndActivo(200L, 1)).thenReturn(List.of());

        assertThatThrownBy(() -> service.validarSaldoAprobacion(s))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("proporcional");
    }

    // ── Adelanto: dentro del proporcional — PASA ──
    @Test
    void adelanto_dentro_del_saldo_proporcional_no_lanza() {
        SolicitudRrhh s = solicitud(601L, 201L);
        // 4 meses → 10 días disponibles; pide 8 → OK.
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(601L, 1))
                .thenReturn(List.of(det("ADELANTO", 8, LocalDate.of(2026, 5, 5))));
        when(tiempoServicioService.calcular(eq(201L), any()))
                .thenReturn(new com.indeci.rrhh.dto.TiempoServicioDto(
                        201L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1),
                        0, 4, 0, 120, 1, false));
        when(incidenciaLaboralProvider.obtenerDiasNoComputables(eq(201L), any(), any())).thenReturn(0);
        when(vacacionRepository.findByEmpleadoIdAndActivo(201L, 1)).thenReturn(List.of());

        service.validarSaldoAprobacion(s); // no lanza
    }

    // ── Adelanto: LSG del período reduce meses efectivos → reduce el tope ──
    @Test
    void adelanto_con_lsg_reduce_el_saldo_proporcional() {
        SolicitudRrhh s = solicitud(602L, 202L);
        // 4 meses (120 d) − 35 d LSG = 85 d → 2 meses efectivos → 5 días disponibles. Pide 8 → RECHAZA.
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(602L, 1))
                .thenReturn(List.of(det("ADELANTO", 8, LocalDate.of(2026, 5, 5))));
        when(tiempoServicioService.calcular(eq(202L), any()))
                .thenReturn(new com.indeci.rrhh.dto.TiempoServicioDto(
                        202L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1),
                        0, 4, 0, 120, 1, false));
        when(incidenciaLaboralProvider.obtenerDiasNoComputables(eq(202L), any(), any())).thenReturn(35);
        when(vacacionRepository.findByEmpleadoIdAndActivo(202L, 1)).thenReturn(List.of());

        assertThatThrownBy(() -> service.validarSaldoAprobacion(s))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("proporcional");
    }

    // ── Hub Vacacional: reprogramar con período elegido del dropdown marca el origen ──
    @Test
    void reprogramacion_con_vacacionOrigenId_marca_periodo_origen_como_sustituido() {
        SolicitudRrhh s = solicitud(700L, 300L);
        SolicitudVacacionDet actual = det("REPROG_ACTUAL", 20, LocalDate.of(2026, 6, 1));
        actual.setVacacionOrigenId(900L);
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(700L, 1)).thenReturn(List.of(
                actual, det("REPROG_NUEVO", 20, LocalDate.of(2026, 8, 1))));

        Vacacion origen = vacacionOrigen(900L, 300L);
        when(vacacionRepository.findById(900L)).thenReturn(java.util.Optional.of(origen));

        service.procesarAprobacionVacaciones(s, null);

        assertThat(origen.getEstado()).isEqualTo("SUSTITUIDO");
        verify(vacacionRepository).save(origen);
    }

    // ── Hub Vacacional (IDOR): el período origen debe pertenecer al mismo empleado ──
    @Test
    void reprogramacion_con_vacacionOrigenId_de_otro_empleado_lanza_negocio() {
        SolicitudRrhh s = solicitud(701L, 301L);
        SolicitudVacacionDet actual = det("REPROG_ACTUAL", 20, LocalDate.of(2026, 6, 1));
        actual.setVacacionOrigenId(901L);
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(701L, 1)).thenReturn(List.of(
                actual, det("REPROG_NUEVO", 20, LocalDate.of(2026, 8, 1))));

        // El período 901 pertenece a OTRO empleado (999), no al solicitante (301).
        when(vacacionRepository.findById(901L))
                .thenReturn(java.util.Optional.of(vacacionOrigen(901L, 999L)));

        assertThatThrownBy(() -> service.procesarAprobacionVacaciones(s, null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("no pertenece al solicitante");
    }

    // ── Cero regresión: reprogramación SIN vacacionOrigenId (flujo legacy) no toca vacacionRepository.findById ──
    @Test
    void reprogramacion_sin_vacacionOrigenId_no_busca_origen() {
        SolicitudRrhh s = solicitud(702L, 302L);
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(702L, 1)).thenReturn(List.of(
                det("REPROG_ACTUAL", 20, LocalDate.of(2026, 6, 1)),
                det("REPROG_NUEVO", 20, LocalDate.of(2026, 8, 1))));

        service.procesarAprobacionVacaciones(s, null);

        verify(vacacionRepository, never()).findById(any());
    }

    // ── Fix: obtenerSaldoVacacional debe leer INDECI_VACACION_SALDO, no el código "VAC" ──
    @Test
    void obtenerSaldoVacacional_agrega_desde_vacacion_saldo_y_no_toca_el_catalogo() {
        when(vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(800L, 1))
                .thenReturn(List.of(
                        saldo(800L, 2025, 30, 10),
                        saldo(800L, 2026, 30, 5)));

        com.indeci.rrhh.dto.SaldoVacacionalDto dto = service.obtenerSaldoVacacional(800L);

        assertThat(dto.getDiasGanados()).isEqualByComparingTo("60");
        assertThat(dto.getDiasGozados()).isEqualByComparingTo("15");
        assertThat(dto.getSaldo()).isEqualByComparingTo("45");
        // Nunca debe depender del código "VAC" (huérfano) ni del catálogo de tipos.
        verify(tipoSolicitudRrhhRepository, never()).findByCodigo(any());
    }

    // ── Hub Vacacional: listado de periodos programados disponibles ──
    @Test
    void listarPeriodosProgramados_devuelve_futuros_no_sustituidos() {
        when(vacacionRepository.findByEmpleadoIdAndActivoAndPeriodoDesdeGreaterThanEqualAndEstadoNot(
                eq(400L), eq(1), any(), eq("SUSTITUIDO")))
                .thenReturn(List.of(vacacionOrigen(950L, 400L)));

        var result = service.listarPeriodosProgramados(400L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(950L);
        assertThat(result.get(0).dias()).isEqualTo(20d);
    }

    @Test
    void programacion_suficiente_descuenta_los_dias_pedidos() {
        SolicitudRrhh s = solicitud(502L, 102L);
        when(solicitudVacacionDetRepository.findBySolicitudIdAndActivo(502L, 1))
                .thenReturn(List.of(det("PROGRAMACION", 15, LocalDate.of(2026, 8, 1))));
        VacacionSaldo saldo = saldo(102L, 2026, 30, 0);
        when(vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(102L, 1))
                .thenReturn(List.of(saldo));

        service.procesarAprobacionVacaciones(s, null);

        assertThat(saldo.getDiasGozados()).isEqualTo(15.0);
        verify(vacacionRepository, times(1)).save(any());
    }
}
