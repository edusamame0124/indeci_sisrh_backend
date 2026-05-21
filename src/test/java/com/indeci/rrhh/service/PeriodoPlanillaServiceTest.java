package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AprobacionPeriodoDto;
import com.indeci.rrhh.entity.ConciliacionAirhsp;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.ConciliacionAirhspRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
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
 * Spec 011 — Tests del flujo de aprobación de planilla (Etapa 3 · B7).
 *   - enviarRevision feliz / desde estado inválido
 *   - aprobar: gate 1 (cert presup.), gate 2 (conciliación ROJA), gate 3 (NETO_NO_VA)
 *   - aprobar feliz → APROBADO
 *   - cerrar feliz / desde estado inválido
 *   - reabrir retrocede un paso / falla en ABIERTO
 */
@ExtendWith(MockitoExtension.class)
class PeriodoPlanillaServiceTest {

    @Mock private PeriodoPlanillaRepository repository;
    @Mock private ConciliacionAirhspRepository conciliacionRepository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private PeriodoPlanillaService service;

    private static final Long PERIODO_ID = 7L;
    private static final String PERIODO = "2026-05";

    private PeriodoPlanilla periodo(String estado) {
        PeriodoPlanilla p = new PeriodoPlanilla();
        p.setId(PERIODO_ID);
        p.setPeriodo(PERIODO);
        p.setEstado(estado);
        p.setActivo(1);
        return p;
    }

    private ConciliacionAirhsp conciliacion(String estado) {
        ConciliacionAirhsp c = new ConciliacionAirhsp();
        c.setEstado(estado);
        return c;
    }

    private MovimientoPlanilla movimiento(String estadoNeto) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setEstadoNeto(estadoNeto);
        return m;
    }

    private AprobacionPeriodoDto cert(String nro) {
        AprobacionPeriodoDto d = new AprobacionPeriodoDto();
        d.setNroCertPresup(nro);
        return d;
    }

    // ==================== ENVIAR A REVISIÓN ====================

    @Test
    void enviarRevision_feliz_pasa_a_EN_REVISION() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("ABIERTO")));

        service.enviarRevision(PERIODO_ID);

        ArgumentCaptor<PeriodoPlanilla> capt = ArgumentCaptor.forClass(PeriodoPlanilla.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("EN_REVISION");
    }

    @Test
    void enviarRevision_desde_estado_invalido_lanza_negocio() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("APROBADO")));

        assertThatThrownBy(() -> service.enviarRevision(PERIODO_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("ABIERTO");
    }

    // ==================== APROBAR — GATES ====================

    @Test
    void aprobar_sin_certificacion_presupuestal_lanza_negocio() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("EN_REVISION")));

        assertThatThrownBy(() -> service.aprobar(PERIODO_ID, cert("  ")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("certificación");
    }

    @Test
    void aprobar_con_conciliacion_roja_lanza_negocio() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("EN_REVISION")));
        when(conciliacionRepository.findByPeriodoPlanillaId(PERIODO_ID))
                .thenReturn(List.of(conciliacion("CONCILIADO"), conciliacion("PENDIENTE")));

        assertThatThrownBy(() -> service.aprobar(PERIODO_ID, cert("CERT-2026-001")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("conciliación");
    }

    @Test
    void aprobar_con_movimiento_neto_no_va_lanza_negocio() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("EN_REVISION")));
        when(conciliacionRepository.findByPeriodoPlanillaId(PERIODO_ID))
                .thenReturn(List.of(conciliacion("CONCILIADO")));
        when(movimientoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of(movimiento("BIEN"), movimiento("NETO_NO_VA")));

        assertThatThrownBy(() -> service.aprobar(PERIODO_ID, cert("CERT-2026-001")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("NETO_NO_VA");
    }

    @Test
    void aprobar_feliz_con_gates_ok_pasa_a_APROBADO() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("EN_REVISION")));
        when(conciliacionRepository.findByPeriodoPlanillaId(PERIODO_ID))
                .thenReturn(List.of(conciliacion("CONCILIADO"), conciliacion("JUSTIFICADO")));
        when(movimientoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of(movimiento("BIEN")));

        service.aprobar(PERIODO_ID, cert("CERT-2026-001"));

        ArgumentCaptor<PeriodoPlanilla> capt = ArgumentCaptor.forClass(PeriodoPlanilla.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("APROBADO");
        assertThat(capt.getValue().getNroCertPresup()).isEqualTo("CERT-2026-001");
        assertThat(capt.getValue().getFechaAprobacion()).isNotNull();
    }

    // ==================== CERRAR ====================

    @Test
    void cerrar_feliz_desde_APROBADO_pasa_a_CERRADO() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("APROBADO")));

        service.cerrar(PERIODO_ID);

        ArgumentCaptor<PeriodoPlanilla> capt = ArgumentCaptor.forClass(PeriodoPlanilla.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("CERRADO");
        assertThat(capt.getValue().getFechaCierre()).isNotNull();
    }

    @Test
    void cerrar_desde_estado_invalido_lanza_negocio() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("ABIERTO")));

        assertThatThrownBy(() -> service.cerrar(PERIODO_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("APROBADO");
    }

    // ==================== REABRIR ====================

    @Test
    void reabrir_retrocede_un_paso_de_CERRADO_a_APROBADO() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("CERRADO")));

        service.reabrir(PERIODO_ID);

        ArgumentCaptor<PeriodoPlanilla> capt = ArgumentCaptor.forClass(PeriodoPlanilla.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("APROBADO");
        assertThat(capt.getValue().getFechaCierre()).isNull();
    }

    @Test
    void reabrir_desde_ABIERTO_lanza_negocio() {
        when(repository.findById(PERIODO_ID)).thenReturn(Optional.of(periodo("ABIERTO")));

        assertThatThrownBy(() -> service.reabrir(PERIODO_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("ABIERTO");
    }
}
