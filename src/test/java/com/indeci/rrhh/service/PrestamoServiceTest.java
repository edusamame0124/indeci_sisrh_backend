package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PrestamoDto;
import com.indeci.rrhh.dto.PrestamoResponseDto;
import com.indeci.rrhh.entity.Prestamo;
import com.indeci.rrhh.repository.PrestamoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 010 / PANTALLA-08 — Tests del servicio de préstamos.
 *   - registrar feliz → ACTIVO con 0 cuotas pagadas
 *   - registrar monto negativo → NegocioException
 *   - registrar pago de la última cuota → CANCELADO
 *   - listar → saldo pendiente derivado
 */
@ExtendWith(MockitoExtension.class)
class PrestamoServiceTest {

    @Mock private PrestamoRepository repository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private PrestamoService service;

    private static final Long EMPLEADO_ID = 41L;

    @Test
    void registrar_caso_feliz_crea_prestamo_activo() {
        service.registrar(dto(1200.0, 12, 100.0));

        ArgumentCaptor<Prestamo> capt = ArgumentCaptor.forClass(Prestamo.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("ACTIVO");
        assertThat(capt.getValue().getCuotasPagadas()).isZero();
    }

    @Test
    void registrar_monto_negativo_lanza_negocio() {
        assertThatThrownBy(() -> service.registrar(dto(-1.0, 12, 100.0)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void registrarPago_de_la_ultima_cuota_cancela_el_prestamo() {
        Prestamo prestamo = new Prestamo();
        prestamo.setId(1L);
        prestamo.setEstado("ACTIVO");
        prestamo.setNumeroCuotas(3);
        prestamo.setCuotasPagadas(2);
        prestamo.setMontoTotal(300.0);
        prestamo.setCuotaMensual(100.0);
        when(repository.findById(1L)).thenReturn(Optional.of(prestamo));

        service.registrarPago(1L);

        ArgumentCaptor<Prestamo> capt = ArgumentCaptor.forClass(Prestamo.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getCuotasPagadas()).isEqualTo(3);
        assertThat(capt.getValue().getEstado()).isEqualTo("CANCELADO");
    }

    @Test
    void listarPorEmpleado_deriva_el_saldo_pendiente() {
        Prestamo prestamo = new Prestamo();
        prestamo.setId(1L);
        prestamo.setEmpleadoId(EMPLEADO_ID);
        prestamo.setMontoTotal(1200.0);
        prestamo.setNumeroCuotas(12);
        prestamo.setCuotaMensual(100.0);
        prestamo.setCuotasPagadas(3);
        prestamo.setEstado("ACTIVO");
        when(repository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(prestamo));

        List<PrestamoResponseDto> filas = service.listarPorEmpleado(EMPLEADO_ID);

        // 1200 − 3 × 100 = 900
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getSaldoPendiente()).isEqualTo(900.0);
    }

    private PrestamoDto dto(double montoTotal, int numeroCuotas, double cuotaMensual) {
        PrestamoDto d = new PrestamoDto();
        d.setEmpleadoId(EMPLEADO_ID);
        d.setDescripcion("Préstamo administrativo");
        d.setMontoTotal(montoTotal);
        d.setNumeroCuotas(numeroCuotas);
        d.setCuotaMensual(cuotaMensual);
        return d;
    }
}
