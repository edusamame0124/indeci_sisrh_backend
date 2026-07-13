package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudVacacionDetDto;
import com.indeci.rrhh.entity.Vacacion;
import com.indeci.rrhh.repository.FeriadoRepository;
import com.indeci.rrhh.repository.VacacionRepository;

/**
 * Art. 35 D.S. 013-2019-PCM — fraccionamiento en días HÁBILES por PERÍODO vacacional.
 * Bolsa de 7 días hábiles (b), tope 4/semana (c), media jornada (0.5). El servidor recalcula
 * los días hábiles (no confía en el cliente). Anclas 2026 (2026-07-11 = sábado):
 * 07-06 lun … 07-10 vie; 07-13 lun … 07-17 vie.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhFraccionamientoArt35Test {

    private static final Long EMP = 90L;

    @Mock private VacacionRepository vacacionRepository;
    @Mock private FeriadoRepository feriadoRepository;

    @InjectMocks private SolicitudRrhhService service;

    @BeforeEach
    void stubs() {
        lenient().when(feriadoRepository.findByAnioInAndActivo(any(), eq(1))).thenReturn(List.of());
        lenient().when(vacacionRepository.findByEmpleadoIdAndActivo(eq(EMP), eq(1))).thenReturn(List.of());
    }

    private SolicitudVacacionDetDto det(String tipo, LocalDate ini, LocalDate fin, Double dias) {
        SolicitudVacacionDetDto d = new SolicitudVacacionDetDto();
        d.setTipo(tipo);
        d.setFechaInicio(ini);
        d.setFechaFin(fin);
        d.setTotalDias(dias);
        return d;
    }

    private SolicitudVacacionDetDto actual() {
        return det("FRACC_ACTUAL", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 8, 4), 30d);
    }

    @Test
    @DisplayName("Caso feliz: 1 fracción de 1 día hábil → no lanza")
    void casoFeliz() {
        List<SolicitudVacacionDetDto> d = List.of(
                actual(),
                det("FRACC_1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 6), 1d));
        assertDoesNotThrow(() -> service.validarFraccionamientoArt35(d, EMP));
    }

    @Test
    @DisplayName("Flujo directo (sin período origen): fracciona el saldo sin FRACC_ACTUAL → no lanza")
    void sinPeriodoActual_directo() {
        List<SolicitudVacacionDetDto> d = List.of(
                det("FRACC_1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 6), 1d));
        assertDoesNotThrow(() -> service.validarFraccionamientoArt35(d, EMP));
    }

    @Test
    @DisplayName("Media jornada válida (mismo día hábil, 0.5) → no lanza")
    void mediaJornadaValida() {
        List<SolicitudVacacionDetDto> d = List.of(
                actual(),
                det("FRACC_1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 6), 0.5d));
        assertDoesNotThrow(() -> service.validarFraccionamientoArt35(d, EMP));
    }

    @Test
    @DisplayName("Media jornada inválida (rango > 1 día) → NegocioException")
    void mediaJornadaRangoInvalido() {
        List<SolicitudVacacionDetDto> d = List.of(
                actual(),
                det("FRACC_1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 7), 0.5d));
        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarFraccionamientoArt35(d, EMP));
        assertTrue(ex.getMessage().contains("media jornada"));
    }

    @Test
    @DisplayName("Días hábiles del cliente NO coinciden con el recálculo → NegocioException")
    void diasHabilesManipulados() {
        // Lun–Mié = 3 hábiles; el cliente envía 2.
        List<SolicitudVacacionDetDto> d = List.of(
                actual(),
                det("FRACC_1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8), 2d));
        assertThrows(NegocioException.class, () -> service.validarFraccionamientoArt35(d, EMP));
    }

    @Test
    @DisplayName("Regla b: supera 7 días hábiles en el período → NegocioException")
    void superaBolsaSieteHabiles() {
        // Semana 1: Lun–Jue = 4; Semana 2: Lun–Jue = 4 → 8 > 7 (cada semana ≤ 4, así b salta antes que c).
        List<SolicitudVacacionDetDto> d = List.of(
                actual(),
                det("FRACC_1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 9), 4d),
                det("FRACC_2", LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 16), 4d));
        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarFraccionamientoArt35(d, EMP));
        assertTrue(ex.getMessage().contains("7 días hábiles"));
    }

    @Test
    @DisplayName("Regla c: más de 4 días hábiles en una semana → NegocioException")
    void superaCuatroPorSemana() {
        // Lun–Vie = 5 hábiles en la misma semana (≤ 7 total, pero > 4/semana).
        List<SolicitudVacacionDetDto> d = List.of(
                actual(),
                det("FRACC_1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 10), 5d));
        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarFraccionamientoArt35(d, EMP));
        assertTrue(ex.getMessage().contains("4 días hábiles"));
    }

    @Test
    @DisplayName("Regla b con histórico: 6 ya gozados + 2 nuevos = 8 > 7 → NegocioException")
    void superaBolsaConHistorico() {
        Vacacion hist = new Vacacion();
        hist.setEmpleadoId(EMP);
        hist.setAnioPeriodo(2026);
        hist.setDias(6d);
        hist.setEstado("GOZADO");
        hist.setActivo(1);
        hist.setPeriodoDesde(LocalDate.of(2026, 6, 1));
        hist.setPeriodoHasta(LocalDate.of(2026, 6, 1));
        when(vacacionRepository.findByEmpleadoIdAndActivo(EMP, 1)).thenReturn(List.of(hist));

        List<SolicitudVacacionDetDto> d = List.of(
                actual(),
                det("FRACC_1", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 7), 2d));
        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarFraccionamientoArt35(d, EMP));
        assertTrue(ex.getMessage().contains("7 días hábiles"));
    }

    @Test
    @DisplayName("Sin fracciones → NegocioException")
    void sinFracciones() {
        assertThrows(NegocioException.class,
                () -> service.validarFraccionamientoArt35(List.of(), EMP));
    }
}
