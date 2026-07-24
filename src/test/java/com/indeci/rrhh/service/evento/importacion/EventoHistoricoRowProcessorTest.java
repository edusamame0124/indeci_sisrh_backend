package com.indeci.rrhh.service.evento.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.Estado;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.FilaResultadoDto;

/**
 * V012_42 F2 — {@code EventoHistoricoRowProcessor}: resuelve DNI→empleado y MOTIVO→tipo,
 * valida idempotencia/solape e inserta. Cubre caso feliz, caso de error normativo (DNI/motivo
 * no resuelto) y casos de borde (duplicado exacto, solape, múltiples empleados por persona).
 */
@ExtendWith(MockitoExtension.class)
class EventoHistoricoRowProcessorTest {

    @Mock private PersonaRepository personaRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private TipoEventoRepository tipoEventoRepository;
    @Mock private EmpleadoEventoRepository empleadoEventoRepository;
    @Spy private EventoHistoricoMotivoMapper motivoMapper = new EventoHistoricoMotivoMapper();

    @InjectMocks private EventoHistoricoRowProcessor processor;

    private static final Long PERSONA_ID = 900L;
    private static final Long EMPLEADO_ID = 55L;

    private EventoHistoricoRowRaw filaLsg() {
        EventoHistoricoRowRaw fila = new EventoHistoricoRowRaw(10);
        fila.put(EventoHistoricoColumna.DNI, "41507811");
        fila.put(EventoHistoricoColumna.MOTIVO, "LICENCIA SIN GOCE");
        fila.put(EventoHistoricoColumna.SERVIDOR, "CARPIO RODRIGUEZ JAIME");
        fila.put(EventoHistoricoColumna.N_RESOLUCION, "049-2020-INDECI/6.1");
        fila.put(EventoHistoricoColumna.FECHA_INICIO, LocalDateTime.of(2023, 8, 18, 0, 0));
        fila.put(EventoHistoricoColumna.FECHA_FIN, LocalDateTime.of(2023, 8, 25, 0, 0));
        fila.put(EventoHistoricoColumna.TOTAL_DIAS, 8.0);
        return fila;
    }

    private Persona persona() {
        Persona p = new Persona();
        p.setId(PERSONA_ID);
        return p;
    }

    private Empleado empleado(Long id, String estado) {
        Empleado e = new Empleado();
        e.setId(id);
        e.setEstado(estado);
        return e;
    }

    private TipoEvento tipoLsg() {
        TipoEvento t = new TipoEvento();
        t.setId(3L);
        t.setCodigo("LICENCIA_SIN_GOCE");
        t.setActivo(1);
        return t;
    }

    @Test
    void caso_feliz_inserta_evento_validado() {
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID))
                .thenReturn(List.of(empleado(EMPLEADO_ID, "ACTIVO")));
        when(tipoEventoRepository.findByCodigo("LICENCIA_SIN_GOCE")).thenReturn(Optional.of(tipoLsg()));
        when(empleadoEventoRepository.existsByEmpleadoIdAndTipoEventoIdAndFechaInicioAndActivo(
                eq(EMPLEADO_ID), eq(3L), eq(LocalDate.of(2023, 8, 18)), eq(1))).thenReturn(false);
        when(empleadoEventoRepository.findSolapados(eq(EMPLEADO_ID), any(), any(), isNull()))
                .thenReturn(List.of());

        FilaResultadoDto resultado = processor.procesar(filaLsg());

        assertThat(resultado.estado()).isEqualTo(Estado.OK);
        ArgumentCaptor<EmpleadoEvento> captor = ArgumentCaptor.forClass(EmpleadoEvento.class);
        verify(empleadoEventoRepository).save(captor.capture());
        EmpleadoEvento guardado = captor.getValue();
        assertThat(guardado.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(guardado.getTipoEventoId()).isEqualTo(3L);
        assertThat(guardado.getFechaInicio()).isEqualTo(LocalDate.of(2023, 8, 18));
        assertThat(guardado.getFechaFin()).isEqualTo(LocalDate.of(2023, 8, 25));
        assertThat(guardado.getDiasAfectos()).isEqualTo(8);
        assertThat(guardado.getEstado()).isEqualTo("VALIDADO");
        assertThat(guardado.getPeriodo()).isEqualTo("202308");
        assertThat(guardado.getObservacion()).contains("049-2020-INDECI/6.1");
    }

    @Test
    void sancion_pad_preserva_texto_original_en_observacion() {
        EventoHistoricoRowRaw fila = new EventoHistoricoRowRaw(20);
        fila.put(EventoHistoricoColumna.DNI, "41507811");
        fila.put(EventoHistoricoColumna.MOTIVO, "SANCION PAD");
        fila.put(EventoHistoricoColumna.N_RESOLUCION, "010-2023-INDECI/6.1");
        fila.put(EventoHistoricoColumna.FECHA_INICIO, LocalDateTime.of(2023, 1, 1, 0, 0));
        fila.put(EventoHistoricoColumna.FECHA_FIN, LocalDateTime.of(2023, 1, 5, 0, 0));

        TipoEvento tipoSuspension = new TipoEvento();
        tipoSuspension.setId(4L);
        tipoSuspension.setCodigo("SUSPENSION_HISTORICA");
        tipoSuspension.setActivo(1);

        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID))
                .thenReturn(List.of(empleado(EMPLEADO_ID, "ACTIVO")));
        when(tipoEventoRepository.findByCodigo("SUSPENSION_HISTORICA"))
                .thenReturn(Optional.of(tipoSuspension));
        when(empleadoEventoRepository.existsByEmpleadoIdAndTipoEventoIdAndFechaInicioAndActivo(
                any(), any(), any(), any())).thenReturn(false);
        when(empleadoEventoRepository.findSolapados(any(), any(), any(), isNull())).thenReturn(List.of());

        FilaResultadoDto resultado = processor.procesar(fila);

        assertThat(resultado.estado()).isEqualTo(Estado.OK);
        ArgumentCaptor<EmpleadoEvento> captor = ArgumentCaptor.forClass(EmpleadoEvento.class);
        verify(empleadoEventoRepository).save(captor.capture());
        assertThat(captor.getValue().getObservacion()).contains("SANCION PAD");
    }

    @Test
    void dni_sin_persona_se_rechaza() {
        EventoHistoricoRowRaw fila = filaLsg();
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.empty());

        FilaResultadoDto resultado = processor.procesar(fila);

        assertThat(resultado.estado()).isEqualTo(Estado.ERROR);
        assertThat(resultado.mensaje()).contains("no coincide con ningún empleado");
        verify(empleadoEventoRepository, never()).save(any());
    }

    @Test
    void persona_sin_empleado_se_rechaza() {
        EventoHistoricoRowRaw fila = filaLsg();
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID)).thenReturn(List.of());

        FilaResultadoDto resultado = processor.procesar(fila);

        assertThat(resultado.estado()).isEqualTo(Estado.ERROR);
        verify(empleadoEventoRepository, never()).save(any());
    }

    @Test
    void prefiere_empleado_activo_cuando_hay_varios_por_persona() {
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID)).thenReturn(List.of(
                empleado(1L, "CESADO"),
                empleado(EMPLEADO_ID, "ACTIVO")));
        when(tipoEventoRepository.findByCodigo("LICENCIA_SIN_GOCE")).thenReturn(Optional.of(tipoLsg()));
        when(empleadoEventoRepository.existsByEmpleadoIdAndTipoEventoIdAndFechaInicioAndActivo(
                any(), any(), any(), any())).thenReturn(false);
        when(empleadoEventoRepository.findSolapados(any(), any(), any(), isNull())).thenReturn(List.of());

        processor.procesar(filaLsg());

        ArgumentCaptor<EmpleadoEvento> captor = ArgumentCaptor.forClass(EmpleadoEvento.class);
        verify(empleadoEventoRepository).save(captor.capture());
        assertThat(captor.getValue().getEmpleadoId()).isEqualTo(EMPLEADO_ID);
    }

    @Test
    void motivo_sin_mapeo_se_rechaza_sin_consultar_dni() {
        EventoHistoricoRowRaw fila = filaLsg();
        fila.put(EventoHistoricoColumna.MOTIVO, "VACACIONES");
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID))
                .thenReturn(List.of(empleado(EMPLEADO_ID, "ACTIVO")));

        FilaResultadoDto resultado = processor.procesar(fila);

        assertThat(resultado.estado()).isEqualTo(Estado.ERROR);
        assertThat(resultado.mensaje()).contains("no tiene mapeo");
        verify(tipoEventoRepository, never()).findByCodigo(any());
    }

    @Test
    void fecha_fin_antes_de_inicio_se_rechaza() {
        EventoHistoricoRowRaw fila = new EventoHistoricoRowRaw(30);
        fila.put(EventoHistoricoColumna.DNI, "41507811");
        fila.put(EventoHistoricoColumna.MOTIVO, "LICENCIA SIN GOCE");
        fila.put(EventoHistoricoColumna.FECHA_INICIO, LocalDateTime.of(2023, 8, 25, 0, 0));
        fila.put(EventoHistoricoColumna.FECHA_FIN, LocalDateTime.of(2023, 8, 18, 0, 0));
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID))
                .thenReturn(List.of(empleado(EMPLEADO_ID, "ACTIVO")));
        when(tipoEventoRepository.findByCodigo("LICENCIA_SIN_GOCE")).thenReturn(Optional.of(tipoLsg()));

        FilaResultadoDto resultado = processor.procesar(fila);

        assertThat(resultado.estado()).isEqualTo(Estado.ERROR);
        assertThat(resultado.mensaje()).contains("anterior a la fecha de inicio");
    }

    @Test
    void duplicado_exacto_se_omite_sin_error() {
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID))
                .thenReturn(List.of(empleado(EMPLEADO_ID, "ACTIVO")));
        when(tipoEventoRepository.findByCodigo("LICENCIA_SIN_GOCE")).thenReturn(Optional.of(tipoLsg()));
        when(empleadoEventoRepository.existsByEmpleadoIdAndTipoEventoIdAndFechaInicioAndActivo(
                eq(EMPLEADO_ID), eq(3L), eq(LocalDate.of(2023, 8, 18)), eq(1))).thenReturn(true);

        FilaResultadoDto resultado = processor.procesar(filaLsg());

        assertThat(resultado.estado()).isEqualTo(Estado.DUPLICADO_OMITIDO);
        verify(empleadoEventoRepository, never()).save(any());
        verify(empleadoEventoRepository, never()).findSolapados(any(), any(), any(), any());
    }

    @Test
    void solape_con_evento_existente_se_rechaza() {
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID))
                .thenReturn(List.of(empleado(EMPLEADO_ID, "ACTIVO")));
        when(tipoEventoRepository.findByCodigo("LICENCIA_SIN_GOCE")).thenReturn(Optional.of(tipoLsg()));
        when(empleadoEventoRepository.existsByEmpleadoIdAndTipoEventoIdAndFechaInicioAndActivo(
                any(), any(), any(), any())).thenReturn(false);
        EmpleadoEvento existente = new EmpleadoEvento();
        existente.setId(777L);
        existente.setFechaInicio(LocalDate.of(2023, 8, 20));
        existente.setFechaFin(LocalDate.of(2023, 9, 1));
        when(empleadoEventoRepository.findSolapados(eq(EMPLEADO_ID), any(), any(), isNull()))
                .thenReturn(List.of(existente));

        FilaResultadoDto resultado = processor.procesar(filaLsg());

        assertThat(resultado.estado()).isEqualTo(Estado.ERROR);
        assertThat(resultado.mensaje()).contains("Solapa con el evento ID 777");
        verify(empleadoEventoRepository, never()).save(any());
    }

    @Test
    void tipo_evento_no_sembrado_se_rechaza_defensivamente() {
        when(personaRepository.findByDniNormalizado("41507811")).thenReturn(Optional.of(persona()));
        when(empleadoRepository.findAllByPersonaId(PERSONA_ID))
                .thenReturn(List.of(empleado(EMPLEADO_ID, "ACTIVO")));
        when(tipoEventoRepository.findByCodigo("LICENCIA_SIN_GOCE")).thenReturn(Optional.empty());

        FilaResultadoDto resultado = processor.procesar(filaLsg());

        assertThat(resultado.estado()).isEqualTo(Estado.ERROR);
        assertThat(resultado.mensaje()).contains("no existe o está inactivo");
    }
}
