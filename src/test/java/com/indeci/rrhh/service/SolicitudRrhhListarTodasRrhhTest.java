package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.entity.EstadoSolicitud;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.SolicitudCompensacionDetRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.SolicitudTeletrabajoDetRepository;
import com.indeci.rrhh.repository.SolicitudVacacionDetRepository;
import com.indeci.rrhh.repository.TipoDescansoMedicoRepository;
import com.indeci.rrhh.repository.TipoLicenciaRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoVacacionRepository;

/**
 * Bandeja "Aprobaciones RRHH" ({@code listarTodas()}) — antes usaba {@code findByActivo(1)} sin
 * ningún filtro de estado, mostrando papeletas en BORRADOR (ni enviadas por el empleado) y
 * ENVIADO (esperando al Jefe, no a RRHH) mezcladas con las que sí correspondían. Ahora excluye
 * esos 3 estados, igual que {@code estadosNoEnviadosAunPorElEmpleado()} hace para la bandeja del
 * Jefe (RR.HH. 2026-08-07). Cubre caso feliz, error normativo (verifica que efectivamente se
 * excluyen los 3 estados) y borde (catálogo incompleto no debe romper el filtro).
 *
 * <p>Los mocks de {@code empleadoRepository} en adelante (2026-08-09) no los necesita la
 * lógica de filtrado de {@code listarTodas()} en sí — los necesita {@code convertir()}, que se
 * invoca sobre cada resultado y toca esos repositorios incondicionalmente para armar el DTO.
 * Sin ellos, el caso feliz (que sí devuelve una fila) fallaba con NPE por dependencias nulas.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhListarTodasRrhhTest {

    @Mock
    private SolicitudRrhhRepository repository;

    @Mock
    private EstadoSolicitudRepository estadoSolicitudRepository;

    @Mock
    private EmpleadoRepository empleadoRepository;

    @Mock
    private PersonaRepository personaRepository;

    @Mock
    private TipoSolicitudRrhhRepository tipoSolicitudRepository;

    @Mock
    private TipoLicenciaRepository tipoLicenciaRepository;

    @Mock
    private TipoVacacionRepository tipoVacacionRepository;

    @Mock
    private TipoDescansoMedicoRepository tipoDescansoMedicoRepository;

    @Mock
    private SolicitudVacacionDetRepository solicitudVacacionDetRepository;

    @Mock
    private SolicitudCompensacionDetRepository solicitudCompensacionDetRepository;

    @Mock
    private SolicitudTeletrabajoDetRepository solicitudTeletrabajoDetRepository;

    @InjectMocks
    private SolicitudRrhhService service;

    private EstadoSolicitud estado(Long id, String codigo) {
        EstadoSolicitud e = new EstadoSolicitud();
        e.setId(id);
        e.setCodigo(codigo);
        return e;
    }

    private void stubCatalogoCompleto() {
        when(estadoSolicitudRepository.findByCodigo("BORRADOR"))
                .thenReturn(Optional.of(estado(1L, "BORRADOR")));
        when(estadoSolicitudRepository.findByCodigo("PENDIENTE_FIRMA"))
                .thenReturn(Optional.of(estado(2L, "PENDIENTE_FIRMA")));
        when(estadoSolicitudRepository.findByCodigo("ENVIADO"))
                .thenReturn(Optional.of(estado(3L, "ENVIADO")));
    }

    @Test
    @DisplayName("Caso feliz: devuelve las papeletas que el repositorio filtra como aptas para RRHH")
    void devuelveLoQueElRepositorioFiltra() {
        stubCatalogoCompleto();

        SolicitudRrhh aprobadaPorJefe = new SolicitudRrhh();
        aprobadaPorJefe.setId(99L);
        aprobadaPorJefe.setEstadoSolicitudId(4L);

        when(repository.findByActivoAndEstadoSolicitudIdNotIn(1, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(aprobadaPorJefe));

        var resultado = service.listarTodas();

        assertEquals(1, resultado.size());
        assertEquals(99L, resultado.get(0).getId());
    }

    @Test
    @DisplayName(
            "Error normativo: excluye exactamente BORRADOR, PENDIENTE_FIRMA y ENVIADO "
                    + "(no debe volver a llamar findByActivo(1) sin filtro)")
    void excluyeExactamenteLosTresEstados() {
        stubCatalogoCompleto();

        when(repository.findByActivoAndEstadoSolicitudIdNotIn(eq(1), eq(List.of(1L, 2L, 3L))))
                .thenReturn(List.of());

        service.listarTodas();

        verify(repository).findByActivoAndEstadoSolicitudIdNotIn(1, List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("Borde: catálogo incompleto (falta un estado) no rompe el filtro, solo excluye lo que existe")
    void catalogoIncompleto_noRompe() {
        when(estadoSolicitudRepository.findByCodigo("BORRADOR"))
                .thenReturn(Optional.of(estado(1L, "BORRADOR")));
        when(estadoSolicitudRepository.findByCodigo("PENDIENTE_FIRMA"))
                .thenReturn(Optional.empty());
        when(estadoSolicitudRepository.findByCodigo("ENVIADO"))
                .thenReturn(Optional.of(estado(3L, "ENVIADO")));

        when(repository.findByActivoAndEstadoSolicitudIdNotIn(eq(1), eq(List.of(1L, 3L))))
                .thenReturn(List.of());

        var resultado = service.listarTodas();

        assertEquals(0, resultado.size());
        verify(repository).findByActivoAndEstadoSolicitudIdNotIn(1, List.of(1L, 3L));
    }
}
