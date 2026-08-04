package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;

/**
 * Comisión de Servicio por Día (código 'COMISION_DIA', V012_53) — guard de
 * exclusión mutua con Comisión de Servicio por Horas ('006'): RR.HH.
 * (2026-08-04) exige que un empleado solo pueda tener UNA de las dos
 * papeletas registrada en un mismo rango de fechas. Verifica caso feliz,
 * error normativo (bloqueo en ambas direcciones) y borde (otro tipo no aplica).
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhComisionDiaValidacionTest {

    private static final Long EMPLEADO_ID = 100L;
    private static final Long ID_TIPO_006 = 6L;
    private static final Long ID_TIPO_COMISION_DIA = 99L;

    @Mock
    private SolicitudRrhhRepository repository;

    @Mock
    private TipoSolicitudRrhhRepository tipoSolicitudRepository;

    @InjectMocks
    private SolicitudRrhhService service;

    private TipoSolicitudRrhh tipoComisionHoras() {
        TipoSolicitudRrhh t = new TipoSolicitudRrhh();
        t.setId(ID_TIPO_006);
        t.setCodigo("006");
        t.setNombre("Comisión de servicio por horas");
        return t;
    }

    private TipoSolicitudRrhh tipoComisionDia() {
        TipoSolicitudRrhh t = new TipoSolicitudRrhh();
        t.setId(ID_TIPO_COMISION_DIA);
        t.setCodigo("COMISION_DIA");
        t.setNombre("Comisión de servicio por día");
        return t;
    }

    private SolicitudRrhhDto dto(Long tipoSolicitudId) {
        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setTipoSolicitudId(tipoSolicitudId);
        dto.setFechaInicio(LocalDate.of(2026, 8, 10));
        dto.setFechaFin(LocalDate.of(2026, 8, 12));
        return dto;
    }

    @Test
    @DisplayName("Caso feliz: sin papeletas previas de ningún tipo → no lanza")
    void casoFeliz() {
        // Mockito devuelve false por defecto para exists*(...) no stubeados.
        lenient().when(tipoSolicitudRepository.findByCodigo(any()))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.validarDuplicidad(dto(ID_TIPO_COMISION_DIA), tipoComisionDia(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Error normativo: ya existe '006' solapado → bloquea el registro de COMISION_DIA")
    void bloqueaComisionDiaSiExisteComisionHoras() {
        SolicitudRrhhDto dto = dto(ID_TIPO_COMISION_DIA);

        when(repository
                .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        EMPLEADO_ID, ID_TIPO_COMISION_DIA, 1, dto.getFechaFin(), dto.getFechaInicio()))
                .thenReturn(false);

        when(tipoSolicitudRepository.findByCodigo("006"))
                .thenReturn(Optional.of(tipoComisionHoras()));

        when(repository
                .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        EMPLEADO_ID, ID_TIPO_006, 1, dto.getFechaFin(), dto.getFechaInicio()))
                .thenReturn(true);

        assertThrows(NegocioException.class,
                () -> service.validarDuplicidad(dto, tipoComisionDia(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Borde (simetría): ya existe COMISION_DIA solapada → bloquea el registro de '006'")
    void bloqueaComisionHorasSiExisteComisionDia() {
        SolicitudRrhhDto dto = dto(ID_TIPO_006);

        when(repository
                .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        EMPLEADO_ID, ID_TIPO_006, 1, dto.getFechaFin(), dto.getFechaInicio()))
                .thenReturn(false);

        when(tipoSolicitudRepository.findByCodigo("COMISION_DIA"))
                .thenReturn(Optional.of(tipoComisionDia()));

        when(repository
                .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        EMPLEADO_ID, ID_TIPO_COMISION_DIA, 1, dto.getFechaFin(), dto.getFechaInicio()))
                .thenReturn(true);

        assertThrows(NegocioException.class,
                () -> service.validarDuplicidad(dto, tipoComisionHoras(), EMPLEADO_ID));
    }

    @Test
    @DisplayName("Regresión: otro tipo de papeleta no activa el guard cruzado → no lanza")
    void otroTipoNoAplicaElGuardCruzado() {
        TipoSolicitudRrhh otro = new TipoSolicitudRrhh();
        otro.setId(1L);
        otro.setCodigo("001");

        SolicitudRrhhDto dto = dto(1L);

        lenient().when(repository
                .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        eq(EMPLEADO_ID), eq(1L), eq(1), any(), any()))
                .thenReturn(false);

        assertDoesNotThrow(() -> service.validarDuplicidad(dto, otro, EMPLEADO_ID));

        // El guard cruzado ni siquiera consulta el catálogo para un tipo ajeno.
        org.mockito.Mockito.verifyNoInteractions(tipoSolicitudRepository);
    }

    @Test
    @DisplayName("Edición: excluye la propia solicitud del chequeo cruzado")
    void editarExcluyePropiaSolicitudDelChequeoCruzado() {
        Long solicitudId = 555L;
        SolicitudRrhhDto dto = dto(ID_TIPO_COMISION_DIA);

        lenient().when(repository
                .existsByIdNotAndEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        eq(solicitudId), eq(EMPLEADO_ID), eq(ID_TIPO_COMISION_DIA), eq(1), any(), any()))
                .thenReturn(false);

        when(tipoSolicitudRepository.findByCodigo("006"))
                .thenReturn(Optional.of(tipoComisionHoras()));

        // La única '006' solapada es la que se está editando (excluida por ID) → no debe bloquear.
        when(repository
                .existsByIdNotAndEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        eq(solicitudId), eq(EMPLEADO_ID), eq(ID_TIPO_006), eq(1), any(), any()))
                .thenReturn(false);

        assertDoesNotThrow(() ->
                service.validarDuplicidadEditar(solicitudId, dto, tipoComisionDia(), EMPLEADO_ID));
    }
}
