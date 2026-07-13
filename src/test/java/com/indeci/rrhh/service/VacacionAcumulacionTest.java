package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.indeci.rrhh.dto.AcumulacionDecisionInputDto;
import com.indeci.rrhh.dto.AcumulacionDecisionResponseDto;
import com.indeci.rrhh.entity.VacacionAcumulacionDecision;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.EstadoSolicitudRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.SolicitudVacacionDetRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.repository.VacacionAcumulacionDecisionRepository;
import com.indeci.rrhh.repository.VacacionRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;

/**
 * F9.3 — Acumulación de vacaciones (D.S. 013-2019-PCM): conteo de períodos pendientes de
 * gozar y registro de la decisión de RR.HH. (auditoría append-only, nunca pérdida automática).
 */
@ExtendWith(MockitoExtension.class)
class VacacionAcumulacionTest {

    @Mock VacacionRepository vacacionRepository;
    @Mock SolicitudRrhhRepository solicitudRepository;
    @Mock TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;
    @Mock EstadoSolicitudRepository estadoSolicitudRepository;
    @Mock VacacionSaldoRepository vacacionSaldoRepository;
    @Mock SolicitudVacacionDetRepository solicitudVacacionDetRepository;
    @Mock TiempoServicioService tiempoServicioService;
    @Mock com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta incidenciaLaboralProvider;
    @Mock VacacionAcumulacionDecisionRepository acumulacionDecisionRepository;

    @InjectMocks VacacionService service;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("rrhh.editora", null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private VacacionSaldo saldo(long empleadoId, int anio, double ganados, double gozados) {
        VacacionSaldo v = new VacacionSaldo();
        v.setEmpleadoId(empleadoId);
        v.setAnio(anio);
        v.setDiasGanados(ganados);
        v.setDiasGozados(gozados);
        return v;
    }

    // ── contarPeriodosPendientes: solo cuenta años con saldo realmente pendiente ──
    @Test
    void contarPeriodosPendientes_cuenta_solo_anios_con_saldo_pendiente() {
        List<VacacionSaldo> saldos = List.of(
                saldo(1L, 2023, 30, 30),   // gozado completo → NO cuenta
                saldo(1L, 2024, 30, 0),    // pendiente → cuenta
                saldo(1L, 2025, 30, 10));  // parcial pendiente → cuenta

        assertThat(VacacionService.contarPeriodosPendientes(saldos)).isEqualTo(2);
    }

    @Test
    void contarPeriodosPendientes_lista_vacia_retorna_cero() {
        assertThat(VacacionService.contarPeriodosPendientes(List.of())).isZero();
    }

    @Test
    void contarPeriodosPendientes_tolera_nulos_en_dias() {
        VacacionSaldo v = new VacacionSaldo();
        v.setEmpleadoId(1L);
        v.setAnio(2026);
        v.setDiasGanados(null);
        v.setDiasGozados(null);

        assertThat(VacacionService.contarPeriodosPendientes(List.of(v))).isZero();
    }

    @Test
    void calcularPeriodosAcumulados_delega_en_el_repositorio() {
        when(vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(55L, 1))
                .thenReturn(List.of(
                        saldo(55L, 2024, 30, 0),
                        saldo(55L, 2025, 30, 0),
                        saldo(55L, 2026, 30, 0)));

        assertThat(service.calcularPeriodosAcumulados(55L)).isEqualTo(3);
    }

    // ── registrarDecisionAcumulacion: NUNCA toca VacacionSaldo, solo audita ──
    @Test
    void registrarDecisionAcumulacion_persiste_snapshot_y_no_modifica_saldos() {
        Long empleadoId = 88L;
        when(vacacionSaldoRepository.findByEmpleadoIdAndActivoOrderByAnioAsc(empleadoId, 1))
                .thenReturn(List.of(
                        saldo(empleadoId, 2024, 30, 0),
                        saldo(empleadoId, 2025, 30, 0),
                        saldo(empleadoId, 2026, 30, 0)));

        AcumulacionDecisionInputDto dto = new AcumulacionDecisionInputDto();
        dto.setMotivoDecision("Se autoriza mantener acumulación por necesidad operativa del servicio.");
        dto.setDocumentoSustento("MEMO-2026-0456");

        AcumulacionDecisionResponseDto response = service.registrarDecisionAcumulacion(empleadoId, dto);

        ArgumentCaptor<VacacionAcumulacionDecision> captor =
                ArgumentCaptor.forClass(VacacionAcumulacionDecision.class);
        verify(acumulacionDecisionRepository).save(captor.capture());

        VacacionAcumulacionDecision persisted = captor.getValue();
        assertThat(persisted.getEmpleadoId()).isEqualTo(empleadoId);
        assertThat(persisted.getPeriodosPendientesAlMomento()).isEqualTo(3);
        assertThat(persisted.getMotivoDecision()).isEqualTo(dto.getMotivoDecision());
        assertThat(persisted.getDocumentoSustento()).isEqualTo("MEMO-2026-0456");
        assertThat(persisted.getUsuarioRegistro()).isEqualTo("rrhh.editora");
        assertThat(persisted.getActivo()).isEqualTo(1);

        assertThat(response.periodosPendientesAlMomento()).isEqualTo(3);
        assertThat(response.usuarioRegistro()).isEqualTo("rrhh.editora");

        verify(vacacionSaldoRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void listarDecisionesAcumulacion_mapea_el_historial_completo() {
        VacacionAcumulacionDecision d = new VacacionAcumulacionDecision();
        d.setId(10L);
        d.setEmpleadoId(88L);
        d.setPeriodosPendientesAlMomento(3);
        d.setMotivoDecision("Autorizado por necesidad de servicio");
        d.setDocumentoSustento("MEMO-2026-0456");
        d.setUsuarioRegistro("rrhh.editora");
        d.setActivo(1);
        d.setCreatedAt(LocalDateTime.of(2026, 7, 11, 10, 30));

        when(acumulacionDecisionRepository.findByEmpleadoIdAndActivoOrderByCreatedAtDesc(88L, 1))
                .thenReturn(List.of(d));

        List<AcumulacionDecisionResponseDto> result = service.listarDecisionesAcumulacion(88L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 11, 10, 30));
    }
}
