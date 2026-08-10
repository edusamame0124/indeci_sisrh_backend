package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoJornadaExcepcionDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoJornadaExcepcion;
import com.indeci.rrhh.repository.EmpleadoJornadaExcepcionRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmpleadoJornadaExcepcionServiceTest {

    @Mock private EmpleadoJornadaExcepcionRepository repository;
    @Mock private EmpleadoRepository empleadoRepository;

    @InjectMocks private EmpleadoJornadaExcepcionService service;

    @Test
    void registrar_casoFeliz_guardaConLosCamposEsperados() {
        Empleado empleado = new Empleado();
        empleado.setId(10L);
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
        lenient().when(repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(10L, 1))
                .thenReturn(List.of());

        EmpleadoJornadaExcepcionDto dto = dtoValido();

        service.registrar(dto);

        verify(repository).save(any(EmpleadoJornadaExcepcion.class));
    }

    @Test
    void registrar_solapaConExcepcionActivaExistente_lanzaError() {
        Empleado empleado = new Empleado();
        empleado.setId(10L);
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));

        EmpleadoJornadaExcepcion existente = new EmpleadoJornadaExcepcion();
        existente.setId(1L);
        existente.setEmpleadoId(10L);
        existente.setFechaInicio(LocalDate.of(2026, 8, 1));
        existente.setFechaFin(LocalDate.of(2026, 8, 31));
        when(repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(10L, 1))
                .thenReturn(List.of(existente));

        EmpleadoJornadaExcepcionDto dto = dtoValido();
        dto.setFechaInicio(LocalDate.of(2026, 8, 15));
        dto.setFechaFin(LocalDate.of(2026, 9, 10));

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("se cruza");
    }

    @Test
    void registrar_horaSalidaIgualAHoraIngreso_lanzaError() {
        Empleado empleado = new Empleado();
        empleado.setId(10L);
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));

        EmpleadoJornadaExcepcionDto dto = dtoValido();
        dto.setHoraSalida(dto.getHoraIngreso());

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    void registrar_sinDocumentoAutorizacion_lanzaError() {
        Empleado empleado = new Empleado();
        empleado.setId(10L);
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));

        EmpleadoJornadaExcepcionDto dto = dtoValido();
        dto.setDocumentoAutorizacion(" ");

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("documento");
    }

    private EmpleadoJornadaExcepcionDto dtoValido() {
        EmpleadoJornadaExcepcionDto dto = new EmpleadoJornadaExcepcionDto();
        dto.setEmpleadoId(10L);
        dto.setHoraIngreso("07:00");
        dto.setHoraSalida("15:00");
        dto.setFechaInicio(LocalDate.of(2026, 8, 1));
        dto.setFechaFin(LocalDate.of(2026, 8, 31));
        dto.setDocumentoAutorizacion("Resolución Jefatural N° 123-2026");
        return dto;
    }
}
