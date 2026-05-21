package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ConciliacionAirhspDto;
import com.indeci.rrhh.dto.ConciliacionRevisionDto;
import com.indeci.rrhh.entity.ConciliacionAirhsp;
import com.indeci.rrhh.repository.ConciliacionAirhspRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 010 / M13 — Tests del servicio de conciliación AIRHSP.
 *   - registrar sin diferencia → nace CONCILIADO
 *   - registrar con diferencia → nace PENDIENTE
 *   - registrar duplicado → NegocioException
 *   - revisar JUSTIFICADO sin justificación → NegocioException
 */
@ExtendWith(MockitoExtension.class)
class ConciliacionAirhspServiceTest {

    @Mock private ConciliacionAirhspRepository repository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private ConciliacionAirhspService service;

    private static final Long EMPLEADO_ID   = 41L;
    private static final Long MOVIMIENTO_ID = 100L;
    private static final Long PERIODO_ID    = 7L;

    @Test
    void registrar_sin_diferencia_nace_conciliado() {
        when(repository.findByMovimientoPlanillaIdAndEmpleadoId(MOVIMIENTO_ID, EMPLEADO_ID))
                .thenReturn(Optional.empty());

        service.registrar(dto(3000.00, 3000.00));

        ArgumentCaptor<ConciliacionAirhsp> capt =
                ArgumentCaptor.forClass(ConciliacionAirhsp.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("CONCILIADO");
    }

    @Test
    void registrar_con_diferencia_nace_pendiente() {
        when(repository.findByMovimientoPlanillaIdAndEmpleadoId(MOVIMIENTO_ID, EMPLEADO_ID))
                .thenReturn(Optional.empty());

        service.registrar(dto(3000.00, 2850.00)); // diferencia 150

        ArgumentCaptor<ConciliacionAirhsp> capt =
                ArgumentCaptor.forClass(ConciliacionAirhsp.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    void registrar_duplicado_lanza_negocio() {
        when(repository.findByMovimientoPlanillaIdAndEmpleadoId(MOVIMIENTO_ID, EMPLEADO_ID))
                .thenReturn(Optional.of(new ConciliacionAirhsp()));

        assertThatThrownBy(() -> service.registrar(dto(3000.00, 3000.00)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void revisar_justificado_sin_justificacion_lanza_negocio() {
        ConciliacionAirhsp existente = new ConciliacionAirhsp();
        existente.setId(1L);
        existente.setEstado("PENDIENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        ConciliacionRevisionDto rev = new ConciliacionRevisionDto();
        rev.setEstado("JUSTIFICADO");
        rev.setJustificacion("   "); // en blanco

        assertThatThrownBy(() -> service.revisar(1L, rev))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("justificación");
    }

    @Test
    void revisar_justificado_con_justificacion_actualiza_estado() {
        ConciliacionAirhsp existente = new ConciliacionAirhsp();
        existente.setId(1L);
        existente.setEstado("PENDIENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        ConciliacionRevisionDto rev = new ConciliacionRevisionDto();
        rev.setEstado("JUSTIFICADO");
        rev.setJustificacion("Diferencia por reintegro pendiente de carga en AIRHSP");
        rev.setUsuarioRevisa(9L);

        service.revisar(1L, rev);

        ArgumentCaptor<ConciliacionAirhsp> capt =
                ArgumentCaptor.forClass(ConciliacionAirhsp.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("JUSTIFICADO");
        assertThat(capt.getValue().getUsuarioRevisa()).isEqualTo(9L);
        assertThat(capt.getValue().getFechaRevision()).isNotNull();
    }

    // ============================ HELPER ============================
    private ConciliacionAirhspDto dto(double montoSistema, double montoAirhsp) {
        ConciliacionAirhspDto d = new ConciliacionAirhspDto();
        d.setEmpleadoId(EMPLEADO_ID);
        d.setMovimientoPlanillaId(MOVIMIENTO_ID);
        d.setPeriodoPlanillaId(PERIODO_ID);
        d.setMontoSistema(montoSistema);
        d.setMontoAirhsp(montoAirhsp);
        return d;
    }
}
