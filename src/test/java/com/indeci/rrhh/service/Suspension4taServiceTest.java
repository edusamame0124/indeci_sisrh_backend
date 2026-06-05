package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.Suspension4taRequestDto;
import com.indeci.rrhh.dto.Suspension4taVigenteDto;
import com.indeci.rrhh.entity.Suspension4ta;
import com.indeci.rrhh.repository.Suspension4taRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Suspension4taServiceTest {

    @Mock private Suspension4taRepository repository;
    @Mock private AuditoriaContext auditoriaContext;
    @InjectMocks private Suspension4taService service;

    private static final Long EMP = 41L;
    private static final LocalDate DEVENGUE = LocalDate.of(2026, 5, 1);

    @Test
    void consultarVigente_con_constancia_vigente_devuelve_vigente() {
        Suspension4ta s = constancia(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "C-100");
        when(repository.findVigentes(EMP, DEVENGUE)).thenReturn(List.of(s));

        Suspension4taVigenteDto r = service.consultarVigente(EMP, DEVENGUE);

        assertThat(r.vigente()).isTrue();
        assertThat(r.existeVencida()).isFalse();
        assertThat(r.nroConstancia()).isEqualTo("C-100");
    }

    @Test
    void consultarVigente_sin_constancias_devuelve_no_registrada() {
        when(repository.findVigentes(EMP, DEVENGUE)).thenReturn(List.of());
        when(repository.findByEmpleadoIdAndEstadoOrderByFechaVigIniDesc(EMP, "ACTIVO"))
                .thenReturn(List.of());

        Suspension4taVigenteDto r = service.consultarVigente(EMP, DEVENGUE);

        assertThat(r.vigente()).isFalse();
        assertThat(r.existeVencida()).isFalse();
    }

    @Test
    void consultarVigente_con_constancia_vencida_marca_existeVencida() {
        when(repository.findVigentes(EMP, DEVENGUE)).thenReturn(List.of());
        Suspension4ta vencida = constancia(2L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "C-OLD");
        when(repository.findByEmpleadoIdAndEstadoOrderByFechaVigIniDesc(EMP, "ACTIVO"))
                .thenReturn(List.of(vencida));

        Suspension4taVigenteDto r = service.consultarVigente(EMP, DEVENGUE);

        assertThat(r.vigente()).isFalse();
        assertThat(r.existeVencida()).isTrue();
    }

    @Test
    void crear_sin_fechaVigIni_lanza() {
        Suspension4taRequestDto dto = new Suspension4taRequestDto();
        dto.setEmpleadoId(EMP);
        dto.setFechaVigIni(null);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void crear_con_vigencia_solapada_lanza() {
        Suspension4ta existente =
                constancia(5L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "C-1");
        when(repository.findByEmpleadoIdAndEstadoOrderByFechaVigIniDesc(EMP, "ACTIVO"))
                .thenReturn(List.of(existente));

        Suspension4taRequestDto dto = new Suspension4taRequestDto();
        dto.setEmpleadoId(EMP);
        dto.setFechaVigIni(LocalDate.of(2026, 5, 1)); // dentro de la ventana existente
        dto.setFechaVigFin(LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void crear_ok_guarda_estado_activo() {
        when(repository.findByEmpleadoIdAndEstadoOrderByFechaVigIniDesc(EMP, "ACTIVO"))
                .thenReturn(List.of());
        when(repository.save(any(Suspension4ta.class))).thenAnswer(inv -> {
            Suspension4ta s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });

        Suspension4taRequestDto dto = new Suspension4taRequestDto();
        dto.setEmpleadoId(EMP);
        dto.setNroConstancia("C-NEW");
        dto.setFechaVigIni(LocalDate.of(2026, 1, 1));
        dto.setFechaVigFin(LocalDate.of(2026, 12, 31));

        var resp = service.crear(dto);

        assertThat(resp.getId()).isEqualTo(99L);
        assertThat(resp.getEstado()).isEqualTo("ACTIVO");
        assertThat(resp.getEstadoVigencia()).isIn("VIGENTE", "VENCIDA", "FUTURA", "INACTIVA");
        verify(auditoriaContext).setDetalle(eq("Suspensión 4ta creada — empleado 41, constancia C-NEW"));
    }

    private Suspension4ta constancia(Long id, LocalDate ini, LocalDate fin, String nro) {
        Suspension4ta s = new Suspension4ta();
        s.setId(id);
        s.setEmpleadoId(EMP);
        s.setNroConstancia(nro);
        s.setFechaVigIni(ini);
        s.setFechaVigFin(fin);
        s.setEstado("ACTIVO");
        return s;
    }
}
