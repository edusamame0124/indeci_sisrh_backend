package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SaldoVacacionalDto;
import com.indeci.rrhh.dto.SolicitudVacacionDetDto;

/**
 * Programación de vacaciones — bloqueo TEMPRANO por saldo (Opción 1, fail-early). El empleado
 * solo puede PROGRAMAR días ya ganados; sin saldo debe usar el Adelanto (Art. 10 D.S.
 * 013-2019-PCM). El servidor recalcula contra INDECI_VACACION_SALDO — no confía en el cliente.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhProgramacionSaldoTest {

    private static final Long EMPLEADO_ID = 55L;

    @Mock
    private VacacionService vacacionService;

    @InjectMocks
    private SolicitudRrhhService service;

    private SolicitudVacacionDetDto programacion(double dias) {
        SolicitudVacacionDetDto d = new SolicitudVacacionDetDto();
        d.setTipo("PROGRAMACION");
        d.setFechaInicio(LocalDate.of(2026, 7, 6));
        d.setFechaFin(LocalDate.of(2026, 7, 8));
        d.setTotalDias(dias);
        return d;
    }

    private void stubSaldo(double saldo) {
        SaldoVacacionalDto dto = new SaldoVacacionalDto();
        dto.setSaldo(BigDecimal.valueOf(saldo));
        lenient().when(vacacionService.obtenerSaldoVacacional(eq(EMPLEADO_ID))).thenReturn(dto);
    }

    @Test
    @DisplayName("Saldo suficiente (30 disponible, pide 10) → no lanza")
    void saldoSuficiente_noLanza() {
        stubSaldo(30);
        assertDoesNotThrow(() -> service.validarProgramacion(List.of(programacion(10)), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Saldo exacto (10 disponible, pide 10) → no lanza (borde)")
    void saldoExacto_noLanza() {
        stubSaldo(10);
        assertDoesNotThrow(() -> service.validarProgramacion(List.of(programacion(10)), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Sin saldo (0 disponible, pide 5) → NegocioException que sugiere Adelanto")
    void sinSaldo_sugiereAdelanto() {
        stubSaldo(0);
        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarProgramacion(List.of(programacion(5)), EMPLEADO_ID));
        assertTrue(ex.getMessage().contains("Adelanto de Vacaciones"));
    }

    @Test
    @DisplayName("Saldo insuficiente (3 disponible, pide 5) → NegocioException")
    void saldoInsuficiente_rechaza() {
        stubSaldo(3);
        assertThrows(NegocioException.class,
                () -> service.validarProgramacion(List.of(programacion(5)), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Debe existir exactamente un detalle PROGRAMACION (0 detalles) → NegocioException")
    void faltaDetalleProgramacion_rechaza() {
        assertThrows(NegocioException.class,
                () -> service.validarProgramacion(List.of(), EMPLEADO_ID));
    }
}
