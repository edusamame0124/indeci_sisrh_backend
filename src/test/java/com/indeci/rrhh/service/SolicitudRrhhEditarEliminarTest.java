package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EstadoSolicitud;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.SolicitudCompensacionDetRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.SolicitudTeletrabajoDetRepository;
import com.indeci.rrhh.repository.SolicitudVacacionDetRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;

import io.jsonwebtoken.Claims;

/**
 * Autoservicio de papeletas — F1: Editar y Eliminar (Mis papeletas). Cubre la regla de
 * negocio central: SOLO el dueño puede editar/eliminar su propia papeleta, y SOLO mientras
 * está en BORRADOR (no enviada al jefe). Ambos guards ya viven en el servicio (editar/
 * eliminarBorrador); estos tests fijan el contrato para no regresarlo.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhEditarEliminarTest {

    private static final Long EMPLEADO_ID = 42L;
    private static final Long OTRO_EMPLEADO_ID = 99L;
    private static final Long SOLICITUD_ID = 100L;

    @Mock
    private SolicitudRrhhRepository repository;

    @Mock
    private EmpleadoRepository empleadoRepository;

    @Mock
    private EstadoSolicitudRepository estadoSolicitudRepository;

    @Mock
    private TipoSolicitudRrhhRepository tipoSolicitudRepository;

    @Mock
    private SolicitudVacacionDetRepository solicitudVacacionDetRepository;

    @Mock
    private SolicitudCompensacionDetRepository solicitudCompensacionDetRepository;

    @Mock
    private SolicitudTeletrabajoDetRepository solicitudTeletrabajoDetRepository;

    @Mock
    private AuditoriaContext auditoriaContext;

    @InjectMocks
    private SolicitudRrhhService service;

    @BeforeEach
    void autenticarComoEmpleado() {
        Claims claims = mock(Claims.class);
        lenient().when(claims.get("empleadoId")).thenReturn(EMPLEADO_ID);

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getDetails()).thenReturn(claims);

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private SolicitudRrhh solicitudBorrador(Long empleadoId) {
        SolicitudRrhh s = new SolicitudRrhh();
        s.setId(SOLICITUD_ID);
        s.setEmpleadoId(empleadoId);
        s.setEstadoSolicitudId(1L);
        s.setActivo(1);
        return s;
    }

    private EstadoSolicitud estado(Long id, String codigo) {
        EstadoSolicitud e = new EstadoSolicitud();
        e.setId(id);
        e.setCodigo(codigo);
        return e;
    }

    /** Tipo "simple" (permiso por horas), sin vacación/compensación/licencia/lactancia. */
    private TipoSolicitudRrhh tipoPermisoSimple() {
        TipoSolicitudRrhh t = new TipoSolicitudRrhh();
        t.setId(5L);
        t.setCodigo("001");
        t.setMostrarHoras(0);
        t.setRequiereSustento(0);
        t.setRequiereLugar(0);
        t.setRequiereObservacion(0);
        t.setMostrarLactancia(0);
        t.setMostrarDescansoMedico(0);
        t.setMostrarLicencia(0);
        t.setMostrarVacacion(0);
        t.setMostrarCompensacion(0);
        return t;
    }

    private SolicitudRrhhDto dtoEdicion() {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setTipoSolicitudId(5L);
        dto.setFechaInicio(LocalDate.of(2026, 7, 20));
        dto.setFechaFin(LocalDate.of(2026, 7, 20));
        dto.setMotivo("Actualizado");
        return dto;
    }

    // ==========================================
    // editar()
    // ==========================================

    @Test
    @DisplayName("Dueño edita su propia papeleta en BORRADOR → guarda cambios")
    void editar_dueñoEnBorrador_guardaCambios() {
        SolicitudRrhh existente = solicitudBorrador(EMPLEADO_ID);

        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(new Empleado()));
        when(repository.findById(SOLICITUD_ID)).thenReturn(Optional.of(existente));
        when(estadoSolicitudRepository.findById(1L)).thenReturn(Optional.of(estado(1L, "BORRADOR")));
        when(tipoSolicitudRepository.findById(5L)).thenReturn(Optional.of(tipoPermisoSimple()));

        service.editar(SOLICITUD_ID, dtoEdicion());

        verify(repository).save(existente);
        assertEquals("Actualizado", existente.getMotivo());
    }

    @Test
    @DisplayName(
            "Edita papeleta de tipo NO-compensación (mostrarCompensacion=0) → NO lanza NPE "
                    + "(regresión del bug de validarHorasCompensacion sin guardar por tipo)")
    void editar_tipoNoCompensacion_noLanzaNpe() {
        SolicitudRrhh existente = solicitudBorrador(EMPLEADO_ID);

        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(new Empleado()));
        when(repository.findById(SOLICITUD_ID)).thenReturn(Optional.of(existente));
        when(estadoSolicitudRepository.findById(1L)).thenReturn(Optional.of(estado(1L, "BORRADOR")));
        when(tipoSolicitudRepository.findById(5L)).thenReturn(Optional.of(tipoPermisoSimple()));

        SolicitudRrhhDto dto = dtoEdicion();
        // dto.detallesCompensacion queda null a propósito: simula el payload real que
        // enviaría el diálogo de un permiso simple (no de Compensación).
        service.editar(SOLICITUD_ID, dto);

        verify(repository).save(existente);
    }

    @Test
    @DisplayName("Otro empleado intenta editar una papeleta ajena → NegocioException")
    void editar_noDueño_rechaza() {
        SolicitudRrhh ajena = solicitudBorrador(OTRO_EMPLEADO_ID);

        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(new Empleado()));
        when(repository.findById(SOLICITUD_ID)).thenReturn(Optional.of(ajena));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.editar(SOLICITUD_ID, dtoEdicion()));

        assertEquals("No puede editar solicitudes de otro empleado", ex.getMessage());
    }

    @Test
    @DisplayName("Papeleta ya enviada (estado != BORRADOR) → NegocioException, no se edita")
    void editar_noBorrador_rechaza() {
        SolicitudRrhh enviada = solicitudBorrador(EMPLEADO_ID);
        enviada.setEstadoSolicitudId(2L);

        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(new Empleado()));
        when(repository.findById(SOLICITUD_ID)).thenReturn(Optional.of(enviada));
        when(estadoSolicitudRepository.findById(2L))
                .thenReturn(Optional.of(estado(2L, "PENDIENTE_JEFE")));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.editar(SOLICITUD_ID, dtoEdicion()));

        assertEquals("Solo solicitudes en borrador pueden editarse", ex.getMessage());
    }

    // ==========================================
    // eliminarBorrador()
    // ==========================================

    @Test
    @DisplayName("Dueño elimina su propia papeleta en BORRADOR → soft-delete (ACTIVO=0)")
    void eliminar_dueñoEnBorrador_softDelete() {
        SolicitudRrhh existente = solicitudBorrador(EMPLEADO_ID);

        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(new Empleado()));
        when(repository.findById(SOLICITUD_ID)).thenReturn(Optional.of(existente));
        when(estadoSolicitudRepository.findById(1L)).thenReturn(Optional.of(estado(1L, "BORRADOR")));

        service.eliminarBorrador(SOLICITUD_ID);

        assertEquals(0, existente.getActivo());
        verify(repository).save(existente);
    }

    @Test
    @DisplayName("Otro empleado intenta eliminar una papeleta ajena → NegocioException")
    void eliminar_noDueño_rechaza() {
        SolicitudRrhh ajena = solicitudBorrador(OTRO_EMPLEADO_ID);

        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(new Empleado()));
        when(repository.findById(SOLICITUD_ID)).thenReturn(Optional.of(ajena));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.eliminarBorrador(SOLICITUD_ID));

        assertEquals("No puede eliminar papeletas de otro empleado", ex.getMessage());
    }

    @Test
    @DisplayName("Papeleta ya enviada (estado != BORRADOR) → NegocioException, no se elimina")
    void eliminar_noBorrador_rechaza() {
        SolicitudRrhh enviada = solicitudBorrador(EMPLEADO_ID);
        enviada.setEstadoSolicitudId(2L);

        when(empleadoRepository.findById(EMPLEADO_ID)).thenReturn(Optional.of(new Empleado()));
        when(repository.findById(SOLICITUD_ID)).thenReturn(Optional.of(enviada));
        when(estadoSolicitudRepository.findById(2L))
                .thenReturn(Optional.of(estado(2L, "PENDIENTE_JEFE")));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.eliminarBorrador(SOLICITUD_ID));

        assertEquals(1, enviada.getActivo());
        assertEquals("Solo se puede eliminar una papeleta en borrador (aún no enviada al jefe).",
                ex.getMessage());

        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
