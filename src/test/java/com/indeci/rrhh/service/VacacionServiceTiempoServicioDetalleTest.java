package com.indeci.rrhh.service;

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

import com.indeci.exception.VinculoNoEncontradoException;
import com.indeci.rrhh.dto.DiasNoComputablesDto;
import com.indeci.rrhh.dto.TiempoServicioDetalleDto;
import com.indeci.rrhh.dto.TiempoServicioDto;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.SolicitudVacacionDetRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.repository.VacacionRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;
import com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta;

/**
 * V012_42 F1 — {@code VacacionService.calcularTiempoServicioDetalle}: tiempo de servicio
 * EFECTIVO (bruto 30/360 − no computables, re-desglosado). Cubre caso feliz, caso de error
 * normativo (sin vínculo) y caso de borde (descuento cruza un año completo) — REGLA-07.
 *
 * <p>Blindaje explícito (fuente del riesgo detectado en la auditoría previa): estos mocks NO
 * verifican que {@link TiempoServicioDto} bruto quede intacto — eso ya lo cubre
 * {@code TiempoServicioServiceTest}, que no cambia. Aquí solo se prueba la composición
 * aditiva del DTO de detalle.</p>
 */
@ExtendWith(MockitoExtension.class)
class VacacionServiceTiempoServicioDetalleTest {

    @Mock VacacionRepository vacacionRepository;
    @Mock SolicitudRrhhRepository solicitudRepository;
    @Mock TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;
    @Mock EstadoSolicitudRepository estadoSolicitudRepository;
    @Mock VacacionSaldoRepository vacacionSaldoRepository;
    @Mock SolicitudVacacionDetRepository solicitudVacacionDetRepository;
    @Mock TiempoServicioService tiempoServicioService;
    @Mock IncidenciaLaboralCompuesta incidenciaLaboralCompuesta;

    @InjectMocks VacacionService service;

    private static final Long EMPLEADO_ID = 77L;

    @Test
    void caso_feliz_descuenta_lsg_y_reformatea_30_360() {
        // Bruto: 5 años, 8 meses, 10 días = 5*360 + 8*30 + 10 = 2050 días.
        LocalDate ingreso = LocalDate.of(2020, 1, 1);
        LocalDate corte = LocalDate.of(2025, 9, 11);
        TiempoServicioDto bruto = new TiempoServicioDto(
                EMPLEADO_ID, ingreso, corte, 5, 8, 10, 2050, 1, false);
        when(tiempoServicioService.calcular(eq(EMPLEADO_ID), any())).thenReturn(bruto);
        when(incidenciaLaboralCompuesta.calcularDesglose(eq(EMPLEADO_ID), eq(ingreso), eq(corte)))
                .thenReturn(DiasNoComputablesDto.of(30, 0, 0)); // 30 días de LSG

        TiempoServicioDetalleDto d = service.calcularTiempoServicioDetalle(EMPLEADO_ID);

        // Neto: 2050 - 30 = 2020 días -> 5 años, 7 meses, 10 días.
        assertThat(d.totalDiasEfectivos()).isEqualTo(2020);
        assertThat(d.aniosEfectivos()).isEqualTo(5);
        assertThat(d.mesesEfectivos()).isEqualTo(7);
        assertThat(d.diasEfectivos()).isEqualTo(10);
        // El bruto (TiempoServicioDto) queda intacto, sin tocar.
        assertThat(d.tiempoServicio()).isSameAs(bruto);
        assertThat(d.tiempoServicio().anios()).isEqualTo(5);
        assertThat(d.tiempoServicio().totalDias360()).isEqualTo(2050);
    }

    @Test
    void sin_vinculo_activo_devuelve_detalle_vacio_sin_lanzar() {
        when(tiempoServicioService.calcular(eq(EMPLEADO_ID), any()))
                .thenThrow(new VinculoNoEncontradoException("sin vínculo"));

        TiempoServicioDetalleDto d = service.calcularTiempoServicioDetalle(EMPLEADO_ID);

        assertThat(d.tiempoServicio()).isNull();
        assertThat(d.diasNoComputables().total()).isZero();
        assertThat(d.aniversarioEfectivo()).isNull();
        assertThat(d.totalDiasEfectivos()).isZero();
        assertThat(d.aniosEfectivos()).isZero();
    }

    @Test
    void descuento_que_cruza_un_anio_completo_recalcula_anios() {
        // Bruto: 1 año, 0 meses, 5 días = 365 días.
        LocalDate ingreso = LocalDate.of(2024, 1, 1);
        LocalDate corte = LocalDate.of(2025, 1, 6);
        TiempoServicioDto bruto = new TiempoServicioDto(
                EMPLEADO_ID, ingreso, corte, 1, 0, 5, 365, 1, false);
        when(tiempoServicioService.calcular(eq(EMPLEADO_ID), any())).thenReturn(bruto);
        // 10 días no computables cruzan el límite del año (365 - 10 = 355 < 360).
        when(incidenciaLaboralCompuesta.calcularDesglose(eq(EMPLEADO_ID), eq(ingreso), eq(corte)))
                .thenReturn(DiasNoComputablesDto.of(0, 10, 0));

        TiempoServicioDetalleDto d = service.calcularTiempoServicioDetalle(EMPLEADO_ID);

        assertThat(d.totalDiasEfectivos()).isEqualTo(355);
        assertThat(d.aniosEfectivos()).isZero(); // ya no llega a 1 año completo
        assertThat(d.mesesEfectivos()).isEqualTo(11);
        assertThat(d.diasEfectivos()).isEqualTo(25);
    }

    @Test
    void descuento_mayor_al_bruto_no_produce_efectivo_negativo() {
        LocalDate ingreso = LocalDate.of(2026, 1, 1);
        LocalDate corte = LocalDate.of(2026, 2, 1);
        TiempoServicioDto bruto = new TiempoServicioDto(
                EMPLEADO_ID, ingreso, corte, 0, 1, 0, 30, 1, false);
        when(tiempoServicioService.calcular(eq(EMPLEADO_ID), any())).thenReturn(bruto);
        // Defensivo: no debería ocurrir en producción (no computable > bruto), pero el
        // clamp evita un efectivo negativo si pasara.
        when(incidenciaLaboralCompuesta.calcularDesglose(eq(EMPLEADO_ID), eq(ingreso), eq(corte)))
                .thenReturn(DiasNoComputablesDto.of(0, 0, 50));

        TiempoServicioDetalleDto d = service.calcularTiempoServicioDetalle(EMPLEADO_ID);

        assertThat(d.totalDiasEfectivos()).isZero();
        assertThat(d.aniosEfectivos()).isZero();
        assertThat(d.mesesEfectivos()).isZero();
        assertThat(d.diasEfectivos()).isZero();
    }
}
