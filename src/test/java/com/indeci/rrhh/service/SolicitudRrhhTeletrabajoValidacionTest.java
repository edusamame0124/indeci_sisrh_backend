package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.dto.SolicitudTeletrabajoDetDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;

/**
 * Papeleta de Teletrabajo (Ley N° 31572 / SERVIR) — guard normativo del tipo
 * 'TELETRABAJO'. Verifica el gate de modalidad (resolución en legajo), el caso
 * feliz, el error normativo (sin actividades) y el borde (fecha futura).
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhTeletrabajoValidacionTest {

    private static final Long EMPLEADO_ID = 100L;

    @Mock
    private EmpleadoPlanillaRepository empleadoPlanillaRepository;

    @InjectMocks
    private SolicitudRrhhService service;

    private TipoSolicitudRrhh tipoTeletrabajo() {
        TipoSolicitudRrhh t = new TipoSolicitudRrhh();
        t.setCodigo("TELETRABAJO");
        t.setNombre("Reporte de Teletrabajo");
        return t;
    }

    private SolicitudTeletrabajoDetDto actividad(String desc) {
        SolicitudTeletrabajoDetDto d = new SolicitudTeletrabajoDetDto();
        d.setActividad(desc);
        d.setMedioVerificacion("Informe PDF");
        return d;
    }

    /** Stub del gate: el empleado tiene (o no) resolución de teletrabajo activa. */
    private void habilitarTeletrabajador(int flag) {
        EmpleadoPlanilla planilla = new EmpleadoPlanilla();
        planilla.setEsTeletrabajador(flag);
        lenient().when(
                empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(eq(EMPLEADO_ID), any()))
                .thenReturn(Optional.of(planilla));
    }

    @Test
    @DisplayName("Caso feliz: 1 actividad + fecha de hoy → no lanza (sin gate de resolución)")
    void casoFeliz() {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setFechaInicio(LocalDate.now());
        dto.setModalidadTeletrabajo("PARCIAL");
        dto.setDetallesTeletrabajo(List.of(actividad("Elaboración de informe diario")));

        assertDoesNotThrow(() -> service.validarTeletrabajo(dto, tipoTeletrabajo(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Modalidad ausente o inválida → NegocioException")
    void modalidadInvalida() {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setFechaInicio(LocalDate.now());
        dto.setDetallesTeletrabajo(List.of(actividad("Actividad")));
        // sin modalidad
        assertThrows(NegocioException.class,
                () -> service.validarTeletrabajo(dto, tipoTeletrabajo(), EMPLEADO_ID));

        dto.setModalidadTeletrabajo("OTRA");
        assertThrows(NegocioException.class,
                () -> service.validarTeletrabajo(dto, tipoTeletrabajo(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Sin resolución de teletrabajo: YA NO bloquea (gate removido) → no lanza")
    void sinResolucion_yaNoBloquea() {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setFechaInicio(LocalDate.now());
        dto.setModalidadTeletrabajo("COMPLETA");
        dto.setDetallesTeletrabajo(List.of(actividad("Actividad")));

        assertDoesNotThrow(() -> service.validarTeletrabajo(dto, tipoTeletrabajo(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Error normativo: habilitado pero sin actividades → NegocioException")
    void errorSinActividades() {
        habilitarTeletrabajador(1);

        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setFechaInicio(LocalDate.now());
        dto.setModalidadTeletrabajo("PARCIAL");
        dto.setDetallesTeletrabajo(List.of());

        assertThrows(NegocioException.class,
                () -> service.validarTeletrabajo(dto, tipoTeletrabajo(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Error normativo: habilitado pero actividad en blanco → NegocioException")
    void errorActividadEnBlanco() {
        habilitarTeletrabajador(1);

        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setFechaInicio(LocalDate.now());
        dto.setModalidadTeletrabajo("PARCIAL");
        dto.setDetallesTeletrabajo(List.of(actividad("   ")));

        assertThrows(NegocioException.class,
                () -> service.validarTeletrabajo(dto, tipoTeletrabajo(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Borde: habilitado pero fecha futura → NegocioException")
    void bordeFechaFutura() {
        habilitarTeletrabajador(1);

        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setFechaInicio(LocalDate.now().plusDays(1));
        dto.setModalidadTeletrabajo("PARCIAL");
        dto.setDetallesTeletrabajo(List.of(actividad("Reunión de coordinación")));

        assertThrows(NegocioException.class,
                () -> service.validarTeletrabajo(dto, tipoTeletrabajo(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Regresión: validarHoras excluye TELETRABAJO aunque mostrarHoras=1 → no lanza")
    void validarHorasExcluyeTeletrabajo() {
        TipoSolicitudRrhh tipo = tipoTeletrabajo();
        tipo.setMostrarHoras(1); // seed defectuoso; el guard debe ignorarlo

        SolicitudRrhhDto dto = new SolicitudRrhhDto(); // sin horaInicio/horaFin

        assertDoesNotThrow(() -> service.validarHoras(dto, tipo));
    }

    @Test
    @DisplayName("Regresión: validarObservacion excluye TELETRABAJO aunque requiereObservacion=1 → no lanza")
    void validarObservacionExcluyeTeletrabajo() {
        TipoSolicitudRrhh tipo = tipoTeletrabajo();
        tipo.setRequiereObservacion(1); // seed defectuoso; el guard debe ignorarlo

        SolicitudRrhhDto dto = new SolicitudRrhhDto(); // sin observación

        assertDoesNotThrow(() -> service.validarObservacion(dto, tipo));
    }

    @Test
    @DisplayName("Otro tipo de papeleta: el guard no aplica → no lanza")
    void otroTipoNoAplica() {
        TipoSolicitudRrhh otro = new TipoSolicitudRrhh();
        otro.setCodigo("012"); // VAC

        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setFechaInicio(LocalDate.now().plusDays(5)); // futura, pero no debe importar
        dto.setDetallesTeletrabajo(null);

        assertDoesNotThrow(() -> service.validarTeletrabajo(dto, otro, EMPLEADO_ID));
    }
}
