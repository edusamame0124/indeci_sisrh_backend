package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EncargaturaDto;
import com.indeci.rrhh.dto.EncargaturaResponseDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoEncargatura;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoEncargaturaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;

/**
 * F5.2 — Tests del servicio de encargaturas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmpleadoEncargaturaServiceTest {

    @Mock private EmpleadoEncargaturaRepository repository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private PersonaRepository personaRepository;

    @InjectMocks private EmpleadoEncargaturaService service;

    // ==================== Validaciones de entrada ====================

    @Test
    void crear_titular_igual_a_encargado_lanza() {
        EncargaturaDto dto = base();
        dto.setEmpleadoTitularId(10L);
        dto.setEmpleadoEncargId(10L);
        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("distintas");
    }

    @Test
    void crear_sin_titular_lanza() {
        EncargaturaDto dto = base();
        dto.setEmpleadoTitularId(null);
        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("titular");
    }

    @Test
    void crear_fechaFin_anterior_a_fechaInicio_lanza() {
        EncargaturaDto dto = base();
        dto.setFechaInicio(LocalDate.of(2026, 5, 10));
        dto.setFechaFin(LocalDate.of(2026, 4, 30));
        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("anterior");
    }

    // ==================== Solape ====================

    @Test
    void crear_con_solape_activo_lanza() {
        EncargaturaDto dto = base();
        EmpleadoEncargatura existente = encargatura(99L, 10L, 11L, "2026-05-01", null, "ACTIVO");
        when(repository.findSolapesActivos(
                anyLong(), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("solapa");
    }

    // ==================== Happy path ====================

    @Test
    void crear_happy_path_guarda_estado_ACTIVO() {
        EncargaturaDto dto = base();
        when(repository.findSolapesActivos(anyLong(), any(), any(), any())).thenReturn(List.of());
        configurarPersonas(10L, "Pérez Juan", "12345678");
        configurarPersonas(11L, "Soto María", "87654321");
        when(repository.save(any(EmpleadoEncargatura.class)))
                .thenAnswer(inv -> {
                    EmpleadoEncargatura e = inv.getArgument(0);
                    e.setId(500L);
                    return e;
                });

        EncargaturaResponseDto r = service.crear(dto);

        assertThat(r.getId()).isEqualTo(500L);
        assertThat(r.getEstado()).isEqualTo("ACTIVO");
        assertThat(r.getTitularNombre()).isEqualTo("Pérez Juan");
        assertThat(r.getEncargadoNombre()).isEqualTo("Soto María");
    }

    // ==================== Cerrar ====================

    @Test
    void cerrar_setea_estado_CULMINADO_y_fechaFin() {
        EmpleadoEncargatura entity = encargatura(500L, 10L, 11L, "2026-05-01", null, "ACTIVO");
        when(repository.findById(500L)).thenReturn(Optional.of(entity));
        when(repository.save(any(EmpleadoEncargatura.class))).thenAnswer(inv -> inv.getArgument(0));
        configurarPersonas(10L, "Pérez Juan", "12345678");
        configurarPersonas(11L, "Soto María", "87654321");

        EncargaturaResponseDto r = service.cerrar(500L, LocalDate.of(2026, 6, 30));

        assertThat(r.getEstado()).isEqualTo("CULMINADO");
        assertThat(r.getFechaFin()).isEqualTo(LocalDate.of(2026, 6, 30));
        verify(repository).save(entity);
    }

    @Test
    void cerrar_con_fechaFin_anterior_a_fechaInicio_lanza() {
        EmpleadoEncargatura entity = encargatura(500L, 10L, 11L, "2026-05-01", null, "ACTIVO");
        when(repository.findById(500L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cerrar(500L, LocalDate.of(2026, 4, 1)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    void cerrar_ya_culminada_lanza() {
        EmpleadoEncargatura entity = encargatura(500L, 10L, 11L, "2026-05-01", "2026-06-01", "CULMINADO");
        when(repository.findById(500L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cerrar(500L, null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("ya está culminada");
    }

    // ==================== Listar ====================

    @Test
    void listar_estado_invalido_lanza() {
        assertThatThrownBy(() -> service.listar("INVALIDO"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Estado inválido");
    }

    @Test
    void listar_TODOS_devuelve_todas() {
        when(repository.findAllByOrderByFechaInicioDesc()).thenReturn(List.of(
                encargatura(1L, 10L, 11L, "2026-05-01", null, "ACTIVO"),
                encargatura(2L, 12L, 13L, "2026-04-01", "2026-04-30", "CULMINADO")));
        configurarPersonas(10L, "A", "00000010");
        configurarPersonas(11L, "B", "00000011");
        configurarPersonas(12L, "C", "00000012");
        configurarPersonas(13L, "D", "00000013");

        assertThat(service.listar("TODOS")).hasSize(2);
        assertThat(service.listar(null)).hasSize(2);
    }

    @Test
    void listar_ACTIVO_filtra_por_estado() {
        when(repository.findByEstadoOrderByFechaInicioDesc("ACTIVO")).thenReturn(List.of(
                encargatura(1L, 10L, 11L, "2026-05-01", null, "ACTIVO")));
        configurarPersonas(10L, "A", "00000010");
        configurarPersonas(11L, "B", "00000011");

        List<EncargaturaResponseDto> rows = service.listar("ACTIVO");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEstado()).isEqualTo("ACTIVO");
    }

    // ============================ HELPERS ============================

    private EncargaturaDto base() {
        EncargaturaDto dto = new EncargaturaDto();
        dto.setEmpleadoTitularId(10L);
        dto.setEmpleadoEncargId(11L);
        dto.setFechaInicio(LocalDate.of(2026, 5, 1));
        dto.setFechaFin(LocalDate.of(2026, 5, 31));
        dto.setResolucion("R.A. 100-2026");
        return dto;
    }

    private EmpleadoEncargatura encargatura(
            Long id, Long titularId, Long encargId,
            String fini, String ffin, String estado) {
        EmpleadoEncargatura e = new EmpleadoEncargatura();
        e.setId(id);
        e.setEmpleadoTitularId(titularId);
        e.setEmpleadoEncargId(encargId);
        e.setFechaInicio(LocalDate.parse(fini));
        if (ffin != null) e.setFechaFin(LocalDate.parse(ffin));
        e.setEstado(estado);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    private void configurarPersonas(Long empId, String nombre, String dni) {
        Empleado e = new Empleado();
        e.setId(empId);
        e.setPersonaId(empId);
        lenient().when(empleadoRepository.findById(empId)).thenReturn(Optional.of(e));
        Persona p = new Persona();
        p.setId(empId);
        p.setNombreCompleto(nombre);
        p.setDni(dni);
        lenient().when(personaRepository.findById(empId)).thenReturn(Optional.of(p));
    }
}
