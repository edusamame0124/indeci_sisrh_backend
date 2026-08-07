package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.indeci.rrhh.entity.EstadoSolicitud;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;

/**
 * Guard de duplicidad (validarDuplicidad / validarDuplicidadEditar) — una papeleta que
 * terminó en RECHAZADO_JEFE, RECHAZADO_RRHH o ANULADO ya NO debe "reservar" el rango de
 * fechas frente a un nuevo intento del mismo tipo (RR.HH. 2026-08-07: antes de este fix, el
 * mensaje "El empleado ya tiene una solicitud de este tipo en esas fechas" se disparaba aun
 * cuando la única papeleta previa estaba rechazada). Cubre caso feliz, error normativo
 * (una papeleta VIGENTE sigue bloqueando) y caso de borde (ANULADO, vía edición).
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhDuplicidadPostRechazoTest {

    private static final Long EMPLEADO_ID = 100L;
    private static final Long TIPO_ID = 5L;

    @Mock
    private SolicitudRrhhRepository repository;

    @Mock
    private EstadoSolicitudRepository estadoSolicitudRepository;

    @Mock
    private TipoSolicitudRrhhRepository tipoSolicitudRepository;

    @InjectMocks
    private SolicitudRrhhService service;

    private TipoSolicitudRrhh tipoPermisoSimple() {
        TipoSolicitudRrhh t = new TipoSolicitudRrhh();
        t.setId(TIPO_ID);
        t.setCodigo("001");
        return t;
    }

    private SolicitudRrhhDto dto() {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setTipoSolicitudId(TIPO_ID);
        dto.setFechaInicio(LocalDate.of(2026, 8, 10));
        dto.setFechaFin(LocalDate.of(2026, 8, 10));
        return dto;
    }

    private EstadoSolicitud estado(Long id, String codigo) {
        EstadoSolicitud e = new EstadoSolicitud();
        e.setId(id);
        e.setCodigo(codigo);
        return e;
    }

    @Test
    @DisplayName("Caso feliz: la única papeleta previa en esas fechas está RECHAZADO_RRHH → no bloquea el nuevo intento")
    void permitePapeletaNuevaTrasRechazoRrhh() {
        SolicitudRrhhDto dto = dto();

        when(estadoSolicitudRepository.findByCodigo("RECHAZADO_JEFE")).thenReturn(Optional.empty());
        when(estadoSolicitudRepository.findByCodigo("RECHAZADO_RRHH"))
                .thenReturn(Optional.of(estado(10L, "RECHAZADO_RRHH")));
        when(estadoSolicitudRepository.findByCodigo("ANULADO")).thenReturn(Optional.empty());

        // La consulta real excluye el id de RECHAZADO_RRHH y no encuentra nada más → false.
        when(repository
                .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndEstadoSolicitudIdNotInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        eq(EMPLEADO_ID), eq(TIPO_ID), eq(1), eq(List.of(10L)),
                        eq(dto.getFechaFin()), eq(dto.getFechaInicio())))
                .thenReturn(false);

        assertDoesNotThrow(() -> service.validarDuplicidad(dto, tipoPermisoSimple(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Error normativo: existe una papeleta VIGENTE (no rechazada/anulada) en esas fechas → sigue bloqueando")
    void bloqueaSiExistePapeletaVigente() {
        SolicitudRrhhDto dto = dto();

        when(estadoSolicitudRepository.findByCodigo("RECHAZADO_JEFE")).thenReturn(Optional.empty());
        when(estadoSolicitudRepository.findByCodigo("RECHAZADO_RRHH")).thenReturn(Optional.empty());
        when(estadoSolicitudRepository.findByCodigo("ANULADO")).thenReturn(Optional.empty());

        when(repository
                .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndEstadoSolicitudIdNotInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        eq(EMPLEADO_ID), eq(TIPO_ID), eq(1), any(),
                        eq(dto.getFechaFin()), eq(dto.getFechaInicio())))
                .thenReturn(true);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarDuplicidad(dto, tipoPermisoSimple(), EMPLEADO_ID));

        org.junit.jupiter.api.Assertions.assertEquals(
                "El empleado ya tiene una solicitud de este tipo en esas fechas", ex.getMessage());
    }

    @Test
    @DisplayName("Borde: edición excluye también estado ANULADO (vía validarDuplicidadEditar) → no bloquea")
    void permiteEdicionCuandoUnicaCoincidenciaEstaAnulada() {
        Long solicitudId = 777L;
        SolicitudRrhhDto dto = dto();

        when(estadoSolicitudRepository.findByCodigo("RECHAZADO_JEFE")).thenReturn(Optional.empty());
        when(estadoSolicitudRepository.findByCodigo("RECHAZADO_RRHH")).thenReturn(Optional.empty());
        when(estadoSolicitudRepository.findByCodigo("ANULADO"))
                .thenReturn(Optional.of(estado(30L, "ANULADO")));

        when(repository
                .existsByIdNotAndEmpleadoIdAndTipoSolicitudIdAndActivoAndEstadoSolicitudIdNotInAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        eq(solicitudId), eq(EMPLEADO_ID), eq(TIPO_ID), eq(1), eq(List.of(30L)),
                        eq(dto.getFechaFin()), eq(dto.getFechaInicio())))
                .thenReturn(false);

        assertDoesNotThrow(() ->
                service.validarDuplicidadEditar(solicitudId, dto, tipoPermisoSimple(), EMPLEADO_ID));
    }
}
