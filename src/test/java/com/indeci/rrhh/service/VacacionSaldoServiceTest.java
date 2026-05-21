package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.VacacionSaldoDto;
import com.indeci.rrhh.dto.VacacionSaldoResponseDto;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.VacacionSaldoRepository;
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
 * Spec 010 / PANTALLA-08 — Tests del servicio de saldo de vacaciones.
 *   - guardar nuevo → crea la fila del año
 *   - guardar existente → actualiza (UPSERT)
 *   - guardar sin empleado/año → NegocioException
 *   - listar → saldo de días derivado
 */
@ExtendWith(MockitoExtension.class)
class VacacionSaldoServiceTest {

    @Mock private VacacionSaldoRepository repository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private VacacionSaldoService service;

    private static final Long EMPLEADO_ID = 41L;

    @Test
    void guardar_nuevo_crea_la_fila_del_anio() {
        when(repository.findByEmpleadoIdAndAnioAndActivo(EMPLEADO_ID, 2026, 1))
                .thenReturn(Optional.empty());

        service.guardar(dto(2026, 30.0, 5.0));

        ArgumentCaptor<VacacionSaldo> capt = ArgumentCaptor.forClass(VacacionSaldo.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getAnio()).isEqualTo(2026);
        assertThat(capt.getValue().getDiasGanados()).isEqualTo(30.0);
        assertThat(capt.getValue().getActivo()).isEqualTo(1);
    }

    @Test
    void guardar_existente_actualiza_la_misma_fila() {
        VacacionSaldo existente = new VacacionSaldo();
        existente.setId(7L);
        existente.setEmpleadoId(EMPLEADO_ID);
        existente.setAnio(2026);
        existente.setDiasGanados(30.0);
        existente.setDiasGozados(0.0);
        when(repository.findByEmpleadoIdAndAnioAndActivo(EMPLEADO_ID, 2026, 1))
                .thenReturn(Optional.of(existente));

        service.guardar(dto(2026, 30.0, 12.0));

        ArgumentCaptor<VacacionSaldo> capt = ArgumentCaptor.forClass(VacacionSaldo.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getId()).isEqualTo(7L); // misma fila
        assertThat(capt.getValue().getDiasGozados()).isEqualTo(12.0);
    }

    @Test
    void guardar_sin_empleado_o_anio_lanza_negocio() {
        VacacionSaldoDto d = new VacacionSaldoDto();
        d.setEmpleadoId(EMPLEADO_ID); // sin año
        assertThatThrownBy(() -> service.guardar(d))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("año");
    }

    @Test
    void listarPorEmpleado_deriva_el_saldo_de_dias() {
        VacacionSaldo v = new VacacionSaldo();
        v.setId(1L);
        v.setEmpleadoId(EMPLEADO_ID);
        v.setAnio(2026);
        v.setDiasGanados(30.0);
        v.setDiasGozados(8.0);
        when(repository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(v));

        List<VacacionSaldoResponseDto> filas = service.listarPorEmpleado(EMPLEADO_ID);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getDiasSaldo()).isEqualTo(22.0); // 30 − 8
    }

    private VacacionSaldoDto dto(int anio, double ganados, double gozados) {
        VacacionSaldoDto d = new VacacionSaldoDto();
        d.setEmpleadoId(EMPLEADO_ID);
        d.setAnio(anio);
        d.setDiasGanados(ganados);
        d.setDiasGozados(gozados);
        return d;
    }
}
