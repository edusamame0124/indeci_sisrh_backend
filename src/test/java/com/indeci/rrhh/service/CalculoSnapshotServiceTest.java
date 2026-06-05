package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.entity.CalculoSnapshot;
import com.indeci.rrhh.repository.CalculoSnapshotRepository;

/**
 * FASE 2 — Tests del servicio de snapshot de trazabilidad.
 *
 * <p>Cubre: caso feliz (persiste con JSON + activo), caso de borde (sin
 * parámetros / claves nulas) y delegación de desactivar/listar.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalculoSnapshotServiceTest {

    @Mock private CalculoSnapshotRepository repository;

    private CalculoSnapshotService service;

    private static final Long EMP = 41L;
    private static final String PERIODO = "2026-05";

    @BeforeEach
    void setUp() {
        service = new CalculoSnapshotService(repository, new ObjectMapper());
        when(repository.save(any(CalculoSnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // =================== Caso feliz ===================

    @Test
    void registrar_persiste_con_json_activo_y_createdAt() {
        service.registrar(
                CalculoSnapshotService.registro(EMP, PERIODO, CalculoSnapshotService.REGLA_IR4TA_CAS)
                        .movimiento(100L)
                        .base(new BigDecimal("1800.00"))
                        .resultado(new BigDecimal("144.00"))
                        .formula("(1800.00) × 0.08 = 144.00")
                        .version("2026")
                        .param("tasa", new BigDecimal("0.08"))
                        .param("suspensionVigente", false));

        ArgumentCaptor<CalculoSnapshot> cap = ArgumentCaptor.forClass(CalculoSnapshot.class);
        verify(repository).save(cap.capture());
        CalculoSnapshot s = cap.getValue();

        assertThat(s.getEmpleadoId()).isEqualTo(EMP);
        assertThat(s.getPeriodo()).isEqualTo(PERIODO);
        assertThat(s.getMovimientoPlanillaId()).isEqualTo(100L);
        assertThat(s.getRegla()).isEqualTo("IR4TA_CAS");
        assertThat(s.getBaseCalculo()).isEqualByComparingTo("1800.00");
        assertThat(s.getResultado()).isEqualByComparingTo("144.00");
        assertThat(s.getVersionParametros()).isEqualTo("2026");
        assertThat(s.getActivo()).isEqualTo(1);
        assertThat(s.getCreatedAt()).isNotNull();
        assertThat(s.getParametrosJson()).contains("\"tasa\":0.08");
        assertThat(s.getParametrosJson()).contains("\"suspensionVigente\":false");
    }

    // =================== Caso de borde ===================

    @Test
    void registrar_sin_parametros_deja_json_null() {
        service.registrar(
                CalculoSnapshotService.registro(EMP, PERIODO, CalculoSnapshotService.REGLA_GENERAL));

        ArgumentCaptor<CalculoSnapshot> cap = ArgumentCaptor.forClass(CalculoSnapshot.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getParametrosJson()).isNull();
        assertThat(cap.getValue().getActivo()).isEqualTo(1);
    }

    @Test
    void registro_ignora_claves_nulas_en_parametros() {
        service.registrar(
                CalculoSnapshotService.registro(EMP, PERIODO, CalculoSnapshotService.REGLA_GENERAL)
                        .param(null, "x")
                        .param("uit", new BigDecimal("5350")));

        ArgumentCaptor<CalculoSnapshot> cap = ArgumentCaptor.forClass(CalculoSnapshot.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getParametrosJson()).contains("uit");
        // El valor null bajo clave válida sí se serializa; clave null se ignora.
    }

    // =================== Delegación ===================

    @Test
    void desactivarPrevios_delega_en_repositorio() {
        when(repository.desactivarVigentes(EMP, PERIODO)).thenReturn(3);
        assertThat(service.desactivarPrevios(EMP, PERIODO)).isEqualTo(3);
        verify(repository).desactivarVigentes(EMP, PERIODO);
    }

    @Test
    void desactivarPrevios_con_args_invalidos_no_toca_repositorio() {
        assertThat(service.desactivarPrevios(null, PERIODO)).isZero();
        assertThat(service.desactivarPrevios(EMP, " ")).isZero();
        verify(repository, never()).desactivarVigentes(any(), any());
    }

    @Test
    void listar_devuelve_solo_vigentes() {
        CalculoSnapshot s = new CalculoSnapshot();
        s.setRegla("GENERAL");
        when(repository.findByEmpleadoIdAndPeriodoAndActivoOrderByReglaAscIdAsc(EMP, PERIODO, 1))
                .thenReturn(List.of(s));

        assertThat(service.listar(EMP, PERIODO)).hasSize(1);
        verify(repository).findByEmpleadoIdAndPeriodoAndActivoOrderByReglaAscIdAsc(EMP, PERIODO, 1);
    }

    @Test
    void listar_con_args_invalidos_devuelve_vacio() {
        assertThat(service.listar(null, PERIODO)).isEmpty();
        assertThat(service.listar(EMP, null)).isEmpty();
    }
}
