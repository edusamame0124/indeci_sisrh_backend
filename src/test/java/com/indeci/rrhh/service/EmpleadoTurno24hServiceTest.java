package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoTurno24hDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoTurno24h;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EmpleadoTurno24hRepository;

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
class EmpleadoTurno24hServiceTest {

    @Mock private EmpleadoTurno24hRepository repository;
    @Mock private EmpleadoRepository empleadoRepository;

    @InjectMocks private EmpleadoTurno24hService service;

    @Test
    void registrar_casoFeliz_guardaConLosCamposEsperados() {
        Empleado empleado = new Empleado();
        empleado.setId(10L);
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));
        lenient().when(repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(10L, 1))
                .thenReturn(List.of());

        service.registrar(dtoValido());

        verify(repository).save(any(EmpleadoTurno24h.class));
    }

    @Test
    void registrar_solapaConTurnoActivoExistente_lanzaError() {
        Empleado empleado = new Empleado();
        empleado.setId(10L);
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));

        EmpleadoTurno24h existente = new EmpleadoTurno24h();
        existente.setId(1L);
        existente.setEmpleadoId(10L);
        existente.setFechaInicio(LocalDate.of(2026, 8, 1));
        existente.setFechaFin(LocalDate.of(2026, 8, 31));
        when(repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(10L, 1))
                .thenReturn(List.of(existente));

        EmpleadoTurno24hDto dto = dtoValido();
        dto.setFechaInicio(LocalDate.of(2026, 8, 15));
        dto.setFechaFin(LocalDate.of(2026, 9, 10));

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("se cruza");
    }

    @Test
    void registrar_sinDocumentoAutorizacion_lanzaError() {
        Empleado empleado = new Empleado();
        empleado.setId(10L);
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));

        EmpleadoTurno24hDto dto = dtoValido();
        dto.setDocumentoAutorizacion(" ");

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("documento");
    }

    @Test
    void registrar_fechaFinAntesDeFechaInicio_lanzaError() {
        Empleado empleado = new Empleado();
        empleado.setId(10L);
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleado));

        EmpleadoTurno24hDto dto = dtoValido();
        dto.setFechaInicio(LocalDate.of(2026, 8, 31));
        dto.setFechaFin(LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> service.registrar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("fecha de fin");
    }

    @Test
    void eliminar_marcaInactivoSinBorrarFisicamente() {
        EmpleadoTurno24h existente = new EmpleadoTurno24h();
        existente.setId(1L);
        existente.setEmpleadoId(10L);
        existente.setActivo(1);
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any(EmpleadoTurno24h.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(1L);

        assertThat(existente.getActivo()).isZero();
        verify(repository).save(existente);
    }

    @Test
    void listarPorEmpleado_calculaEstadoVigenteFuturaVencida() {
        EmpleadoTurno24h vigente = turno(1L, LocalDate.now().minusDays(5), LocalDate.now().plusDays(5));
        EmpleadoTurno24h futura = turno(2L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(10));
        EmpleadoTurno24h vencida = turno(3L, LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));
        when(repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(10L, 1))
                .thenReturn(List.of(vigente, futura, vencida));

        var resultado = service.listarPorEmpleado(10L);

        assertThat(resultado).extracting("estadoVigencia")
                .containsExactly("VIGENTE", "FUTURA", "VENCIDA");
    }

    private EmpleadoTurno24h turno(Long id, LocalDate inicio, LocalDate fin) {
        EmpleadoTurno24h t = new EmpleadoTurno24h();
        t.setId(id);
        t.setEmpleadoId(10L);
        t.setFechaInicio(inicio);
        t.setFechaFin(fin);
        t.setDocumentoAutorizacion("Resolución Jefatural N° 123-2026");
        return t;
    }

    private EmpleadoTurno24hDto dtoValido() {
        EmpleadoTurno24hDto dto = new EmpleadoTurno24hDto();
        dto.setEmpleadoId(10L);
        dto.setFechaInicio(LocalDate.of(2026, 8, 1));
        dto.setFechaFin(LocalDate.of(2026, 8, 31));
        dto.setDocumentoAutorizacion("Resolución Jefatural N° 123-2026");
        return dto;
    }
}
