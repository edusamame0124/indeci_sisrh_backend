package com.indeci.rrhh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvParser;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvValidator;
import com.indeci.rrhh.service.asistencia.AsistenciaImportErroresCsvWriter;
import com.indeci.rrhh.service.asistencia.AsistenciaImportErroresXlsxWriter;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaImportServiceConfirmarTest {

    @Mock private AsistenciaCsvParser csvParser;
    @Mock private AsistenciaCsvValidator csvValidator;
    @Mock private PeriodoPlanillaRepository periodoRepository;
    @Mock private AsistenciaCabeceraRepository cabeceraRepository;
    @Mock private AsistenciaImportacionRepository importacionRepository;
    @Mock private AsistenciaImportacionFilaRepository filaRepository;
    @Mock private BaseAsistenciaResolver baseResolver;
    @Mock private AsistenciaService asistenciaService;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private AsistenciaImportErroresCsvWriter erroresCsvWriter;
    @Mock private AsistenciaImportErroresXlsxWriter erroresXlsxWriter;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private AsistenciaImportService service;

    private AsistenciaImportacion importacion(String estado) {
        AsistenciaImportacion imp = new AsistenciaImportacion();
        imp.setId(1L);
        imp.setPeriodo("2026-05");
        imp.setEstado(estado);
        return imp;
    }

    private PeriodoPlanilla periodo(String estado) {
        PeriodoPlanilla p = new PeriodoPlanilla();
        p.setPeriodo("2026-05");
        p.setEstado(estado);
        return p;
    }

    private AsistenciaImportacionFila fila(Long id, String estado) {
        AsistenciaImportacionFila f = new AsistenciaImportacionFila();
        f.setId(id);
        f.setEstadoFila(estado);
        f.setEmpleadoId(42L);
        f.setAceptadaObservada(0);
        return f;
    }

    // ---------- confirmar: bloqueo por periodo (R3/R9) ----------

    @Test
    void confirmar_periodoCerrado_bloqueaDuro() {
        when(importacionRepository.findById(1L)).thenReturn(Optional.of(importacion("BORRADOR_PREVIEW")));
        when(periodoRepository.findByPeriodoAndActivo("2026-05", 1))
                .thenReturn(Optional.of(periodo("CERRADO")));

        assertThatThrownBy(() -> service.confirmar(1L, null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("CERRADO");
    }

    // ---------- aceptarObservadas (P3) ----------

    @Test
    void aceptarObservadas_sinMotivo_lanzaExcepcion() {
        when(importacionRepository.findById(1L)).thenReturn(Optional.of(importacion("BORRADOR_PREVIEW")));

        assertThatThrownBy(() -> service.aceptarObservadas(1L, List.of(5L), "  "))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void aceptarObservadas_marcaSoloObservadas_conAuditoria() {
        when(importacionRepository.findById(1L)).thenReturn(Optional.of(importacion("BORRADOR_PREVIEW")));
        AsistenciaImportacionFila obs = fila(5L, "OBSERVADA");
        AsistenciaImportacionFila val = fila(6L, "VALIDA");
        when(filaRepository.findByImportacionIdOrderByNumeroFila(1L))
                .thenReturn(List.of(obs, val));

        int aceptadas = service.aceptarObservadas(1L, List.of(5L), "Marca manual validada por jefatura");

        assertThat(aceptadas).isEqualTo(1);
        assertThat(obs.getAceptadaObservada()).isEqualTo(1);
        assertThat(obs.getMotivoAceptaObs()).isEqualTo("Marca manual validada por jefatura");
        assertThat(val.getAceptadaObservada()).isEqualTo(0);
        verify(filaRepository).saveAll(any());
    }

    // ---------- anular (P4) ----------

    @Test
    void anular_sinMotivo_lanzaExcepcion() {
        when(importacionRepository.findById(1L)).thenReturn(Optional.of(importacion("BORRADOR_PREVIEW")));

        assertThatThrownBy(() -> service.anular(1L, ""))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void anular_enPreview_marcaAnulada() {
        when(importacionRepository.findById(1L)).thenReturn(Optional.of(importacion("BORRADOR_PREVIEW")));

        var dto = service.anular(1L, "Cargado por error");

        assertThat(dto.getEstadoImportacion()).isEqualTo("ANULADA");
    }

    @Test
    void anular_confirmadaConPeriodoNoAbierto_bloquea() {
        when(importacionRepository.findById(1L)).thenReturn(Optional.of(importacion("CONFIRMADA")));
        lenient().when(periodoRepository.findByPeriodoAndActivo("2026-05", 1))
                .thenReturn(Optional.of(periodo("APROBADO")));

        assertThatThrownBy(() -> service.anular(1L, "Rectificar"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("ABIERTO");
    }
}
