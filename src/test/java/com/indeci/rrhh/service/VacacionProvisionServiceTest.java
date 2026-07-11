package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;

/**
 * SPEC_VACACIONES F9.1 — fix del umbral de récord por jornada en la provisión anual, y
 * Refactor Récord Anual Estricto (récord evaluado sobre el AÑO DE SERVICIO específico, no
 * sobre la antigüedad acumulada de toda la carrera).
 *
 * <p>Fixture determinístico (jornada): ingreso 01/01/2020, aniversario 2021 (01/01/2021) →
 * un solo vínculo continuo de exactamente 1 año → {@code diasIdealAnio = 360} (30/360
 * US/NASD del año evaluado 2020, +1 inclusivo). Con una incidencia fija de 131 días no
 * computables → {@code diasEfectivos = 229}: por encima del umbral 210 (jornada 5) pero por
 * debajo de 260 (jornada 6) — el caso exacto que expone el bug de jornada.</p>
 */
class VacacionProvisionServiceTest {

    private static final Long EMPLEADO_ID = 77L;
    private static final int ANIO_PERIODO = 2021;
    private static final int DIAS_NO_COMPUTABLES_FIJO = 131; // 360 - 131 = 229 efectivos

    private EmpleadoPlanillaRepository planillaRepository;
    private VacacionSaldoRepository saldoRepository;
    private JornadaRegimenRepository jornadaRegimenRepository;
    private VacacionProvisionService service;

    private void setUp() {
        planillaRepository = mock(EmpleadoPlanillaRepository.class);
        saldoRepository = mock(VacacionSaldoRepository.class);
        jornadaRegimenRepository = mock(JornadaRegimenRepository.class);

        VacacionCalculoService calculoService =
                new VacacionCalculoService((empId, desde, hasta) -> DIAS_NO_COMPUTABLES_FIJO);

        service = new VacacionProvisionService(
                planillaRepository,
                calculoService,
                saldoRepository,
                new TiempoServicioService(planillaRepository),
                jornadaRegimenRepository);

        when(saldoRepository.findByEmpleadoIdInAndActivo(List.of(EMPLEADO_ID), 1))
                .thenReturn(List.of());
    }

    private EmpleadoPlanilla vinculo(Integer diasSemanaOperativo, Long regimenLaboralId) {
        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setId(1L);
        v.setEmpleadoId(EMPLEADO_ID);
        v.setActivo(1);
        v.setFechaInicioContrato(LocalDate.of(2020, 1, 1));
        v.setDiasSemanaOperativo(diasSemanaOperativo);
        v.setRegimenLaboralId(regimenLaboralId);
        return v;
    }

    // ── Caso feliz: sin override ni config de régimen → default 5 → umbral 210 → habilita ──
    @Test
    void default_jornada_5_usa_umbral_210_y_habilita_provision() {
        setUp();
        when(planillaRepository.findByEmpleadoIdInAndActivo(List.of(EMPLEADO_ID), 1))
                .thenReturn(List.of(vinculo(null, null)));

        service.provisionar(EMPLEADO_ID, ANIO_PERIODO);

        org.mockito.ArgumentCaptor<com.indeci.rrhh.entity.VacacionSaldo> capt =
                org.mockito.ArgumentCaptor.forClass(com.indeci.rrhh.entity.VacacionSaldo.class);
        org.mockito.Mockito.verify(saldoRepository).save(capt.capture());
        assertThat(capt.getValue().getDiasGanados()).isEqualTo(30d);
        assertThat(capt.getValue().getOrigen()).isEqualTo("MOTOR_PROVISION");
    }

    // ── Caso normativo: override 6 días/semana → umbral 260 → mismos 230 efectivos → bloquea ──
    @Test
    void override_jornada_6_usa_umbral_260_y_bloquea() {
        setUp();
        when(planillaRepository.findByEmpleadoIdInAndActivo(List.of(EMPLEADO_ID), 1))
                .thenReturn(List.of(vinculo(6, null)));

        assertThatThrownBy(() -> service.provisionar(EMPLEADO_ID, ANIO_PERIODO))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("récord vacacional");

        org.mockito.Mockito.verify(saldoRepository, org.mockito.Mockito.never()).save(any());
    }

    // ── Caso borde: sin override, jornada 6 configurada a nivel de régimen → también bloquea ──
    @Test
    void jornada_de_regimen_sin_override_tambien_aplica_umbral_260() {
        setUp();
        final Long regimenId = 5L;
        when(planillaRepository.findByEmpleadoIdInAndActivo(List.of(EMPLEADO_ID), 1))
                .thenReturn(List.of(vinculo(null, regimenId)));
        JornadaRegimen jr = new JornadaRegimen();
        jr.setDiasSemana(6);
        when(jornadaRegimenRepository.findByRegimenLaboralId(regimenId))
                .thenReturn(java.util.Optional.of(jr));

        assertThatThrownBy(() -> service.provisionar(EMPLEADO_ID, ANIO_PERIODO))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("récord vacacional");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Refactor Récord Anual Estricto — antigüedad multianual, LSG concentrado en
    // el año evaluado. Certifica que el bloqueo YA NO se diluye por años previos.
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Construye un {@link VacacionProvisionService} aislado (mocks propios, no comparte
     * estado con {@link #setUp()}) con una incidencia FIJA de {@code diasNoComputables} para
     * cualquier rango consultado — permite fijar el récord real del año evaluado.
     */
    private VacacionProvisionService servicioConIncidenciaFija(
            EmpleadoPlanillaRepository planillaRepo, VacacionSaldoRepository saldoRepo,
            JornadaRegimenRepository jornadaRepo, int diasNoComputables) {
        VacacionCalculoService calculoService =
                new VacacionCalculoService((empId, desde, hasta) -> diasNoComputables);
        return new VacacionProvisionService(
                planillaRepo, calculoService, saldoRepo,
                new TiempoServicioService(planillaRepo), jornadaRepo);
    }

    private EmpleadoPlanilla vinculoDesde(LocalDate ingreso) {
        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setId(2L);
        v.setEmpleadoId(EMPLEADO_ID);
        v.setActivo(1);
        v.setFechaInicioContrato(ingreso);
        return v;
    }

    /**
     * Antes del fix: {@code ts.totalDias360()} acumulado (~6 años ≈ 2160 días) − 200 LSG del
     * año más reciente ≈ 1960 efectivos — pasaba SIEMPRE, sin importar el récord real del año
     * (el bug reportado en producción: "a todos les coloca 30 días"). Con el fix, se evalúa
     * SOLO el año de servicio recién completado (2025: 01/01-31/12, 360 días ideal) − 200 LSG
     * = 160 efectivos, por debajo del umbral 210 (jornada 5) → bloquea correctamente.
     */
    @Test
    void multiples_anios_de_antiguedad_con_lsg_concentrado_en_el_ultimo_anio_bloquea() {
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc =
                servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 200);

        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(EMPLEADO_ID), 1))
                .thenReturn(List.of(vinculoDesde(LocalDate.of(2020, 1, 1))));
        when(saldoRepo.findByEmpleadoIdInAndActivo(List.of(EMPLEADO_ID), 1)).thenReturn(List.of());

        assertThatThrownBy(() -> svc.provisionar(EMPLEADO_ID, 2026))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("récord vacacional");

        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.never()).save(any());
    }

    /**
     * Contraparte positiva: misma antigüedad multianual (6 años), pero el año evaluado
     * (2025) tiene solo 50 días no computables → 360 − 50 = 310 efectivos ≥ 210 (jornada 5)
     * → SÍ cumple el récord real de ese año específico y habilita la provisión.
     */
    @Test
    void multiples_anios_de_antiguedad_con_record_real_del_anio_habilita_provision() {
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc =
                servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 50);

        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(EMPLEADO_ID), 1))
                .thenReturn(List.of(vinculoDesde(LocalDate.of(2020, 1, 1))));
        when(saldoRepo.findByEmpleadoIdInAndActivo(List.of(EMPLEADO_ID), 1)).thenReturn(List.of());

        svc.provisionar(EMPLEADO_ID, 2026);

        org.mockito.Mockito.verify(saldoRepo).save(any());
    }
}
