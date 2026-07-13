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
import com.indeci.rrhh.repository.VacacionRepository;
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
                jornadaRegimenRepository,
                new com.indeci.audit.context.AuditoriaContext(),
                mock(VacacionRepository.class));

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
        return servicioConIncidenciaFija(
                planillaRepo, saldoRepo, jornadaRepo, mock(VacacionRepository.class), diasNoComputables);
    }

    private VacacionProvisionService servicioConIncidenciaFija(
            EmpleadoPlanillaRepository planillaRepo, VacacionSaldoRepository saldoRepo,
            JornadaRegimenRepository jornadaRepo, VacacionRepository vacacionRepo, int diasNoComputables) {
        VacacionCalculoService calculoService =
                new VacacionCalculoService((empId, desde, hasta) -> diasNoComputables);
        return new VacacionProvisionService(
                planillaRepo, calculoService, saldoRepo,
                new TiempoServicioService(planillaRepo), jornadaRepo,
                new com.indeci.audit.context.AuditoriaContext(), vacacionRepo);
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

    // ══════════════════════════════════════════════════════════════════════════
    // jobProvisionAutomatica — escaneo nocturno: idempotencia + tolerancia a fallos.
    // ══════════════════════════════════════════════════════════════════════════

    private EmpleadoPlanilla vinculoDeEmpleado(Long empleadoId, LocalDate ingreso) {
        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setId(empleadoId);
        v.setEmpleadoId(empleadoId);
        v.setActivo(1);
        v.setFechaInicioContrato(ingreso);
        return v;
    }

    /**
     * Simula el paso del tiempo: un empleado con 3 años de antigüedad que NUNCA fue
     * provisionado (p. ej. el job estuvo apagado desde su ingreso). Sin incidencias, el job
     * debe hacer "catch-up" y generar los 3 años pendientes en una sola corrida.
     */
    @Test
    void job_provisiona_todos_los_anios_pendientes_de_un_empleado_veterano() {
        Long empleadoId = 201L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc =
                servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 0);

        LocalDate ingreso = LocalDate.now().minusYears(3);
        EmpleadoPlanilla vinculo = vinculoDeEmpleado(empleadoId, ingreso);
        when(planillaRepo.findByActivo(1)).thenReturn(List.of(vinculo));
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(vinculo));
        when(saldoRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of());

        svc.jobProvisionAutomatica();

        org.mockito.ArgumentCaptor<com.indeci.rrhh.entity.VacacionSaldo> capt =
                org.mockito.ArgumentCaptor.forClass(com.indeci.rrhh.entity.VacacionSaldo.class);
        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.times(3)).save(capt.capture());
        List<Integer> aniosProvisionados = capt.getAllValues().stream()
                .map(com.indeci.rrhh.entity.VacacionSaldo::getAnio).toList();
        assertThat(aniosProvisionados).containsExactlyInAnyOrder(
                ingreso.getYear() + 1, ingreso.getYear() + 2, ingreso.getYear() + 3);
    }

    /**
     * Idempotencia estricta: si el saldo del año ya existe, el job NO debe intentar crearlo de
     * nuevo — puede correr todas las noches sin duplicar un solo día.
     */
    @Test
    void job_no_duplica_provision_si_ya_existe() {
        Long empleadoId = 202L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc =
                servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 0);

        LocalDate ingreso = LocalDate.now().minusYears(1);
        EmpleadoPlanilla vinculo = vinculoDeEmpleado(empleadoId, ingreso);
        when(planillaRepo.findByActivo(1)).thenReturn(List.of(vinculo));
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(vinculo));

        com.indeci.rrhh.entity.VacacionSaldo existente = new com.indeci.rrhh.entity.VacacionSaldo();
        existente.setEmpleadoId(empleadoId);
        existente.setAnio(ingreso.getYear() + 1);
        when(saldoRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(existente));

        svc.jobProvisionAutomatica();

        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.never()).save(any());
    }

    /**
     * Tolerancia a fallos: un error TÉCNICO (no de negocio) al procesar un empleado no debe
     * interrumpir la provisión del resto de la nómina.
     */
    @Test
    void job_continua_con_los_demas_empleados_si_uno_falla_tecnicamente() {
        Long empleadoConError = 203L;
        Long empleadoOk = 204L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc =
                servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 0);

        LocalDate ingreso = LocalDate.now().minusYears(1);
        EmpleadoPlanilla vConError = vinculoDeEmpleado(empleadoConError, ingreso);
        vConError.setRegimenLaboralId(99L); // fuerza la consulta que lanzará el error técnico
        EmpleadoPlanilla vOk = vinculoDeEmpleado(empleadoOk, ingreso);

        when(planillaRepo.findByActivo(1)).thenReturn(List.of(vConError, vOk));
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoConError), 1))
                .thenReturn(List.of(vConError));
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoOk), 1)).thenReturn(List.of(vOk));
        when(saldoRepo.findByEmpleadoIdInAndActivo(List.of(empleadoConError), 1)).thenReturn(List.of());
        when(saldoRepo.findByEmpleadoIdInAndActivo(List.of(empleadoOk), 1)).thenReturn(List.of());
        when(jornadaRepo.findByRegimenLaboralId(99L)).thenThrow(new RuntimeException("DB caída"));

        svc.jobProvisionAutomatica(); // no debe propagar la excepción hacia afuera

        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.argThat(s -> s.getEmpleadoId().equals(empleadoConError)));
        org.mockito.Mockito.verify(saldoRepo)
                .save(org.mockito.ArgumentMatchers.argThat(s -> s.getEmpleadoId().equals(empleadoOk)));
    }

    /**
     * Un bloqueo NORMATIVO (SIN_RECORD_LEGAL) de un empleado no debe impedir que el job
     * provisione al resto de la nómina en la misma corrida.
     */
    @Test
    void job_bloqueo_por_record_no_detiene_el_job_permite_provisionar_a_los_demas() {
        Long empleadoBloqueado = 205L;
        Long empleadoOk = 206L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);

        // Incidencia depende del empleado: 300 días (bloquea) para uno, 0 (habilita) para el otro.
        VacacionCalculoService calculoService = new VacacionCalculoService(
                (empId, desde, hasta) -> empId.equals(empleadoBloqueado) ? 300 : 0);
        VacacionProvisionService svc = new VacacionProvisionService(
                planillaRepo, calculoService, saldoRepo,
                new TiempoServicioService(planillaRepo), jornadaRepo,
                new com.indeci.audit.context.AuditoriaContext(), mock(VacacionRepository.class));

        LocalDate ingreso = LocalDate.now().minusYears(1);
        EmpleadoPlanilla vBloqueado = vinculoDeEmpleado(empleadoBloqueado, ingreso);
        EmpleadoPlanilla vOk = vinculoDeEmpleado(empleadoOk, ingreso);

        when(planillaRepo.findByActivo(1)).thenReturn(List.of(vBloqueado, vOk));
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoBloqueado), 1))
                .thenReturn(List.of(vBloqueado));
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoOk), 1)).thenReturn(List.of(vOk));
        when(saldoRepo.findByEmpleadoIdInAndActivo(List.of(empleadoBloqueado), 1)).thenReturn(List.of());
        when(saldoRepo.findByEmpleadoIdInAndActivo(List.of(empleadoOk), 1)).thenReturn(List.of());

        svc.jobProvisionAutomatica();

        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.argThat(s -> s.getEmpleadoId().equals(empleadoBloqueado)));
        org.mockito.Mockito.verify(saldoRepo)
                .save(org.mockito.ArgumentMatchers.argThat(s -> s.getEmpleadoId().equals(empleadoOk)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // recalcularProvisionManual — botón "Provisionar Auto". Récord real por tiempo de
    // servicio para "Ganados"; "Gozados" = goce REAL de las papeletas (tabla Vacacion,
    // estado GOZADO) distribuido FIFO — el Excel muere, la papeleta manda.
    // ══════════════════════════════════════════════════════════════════════════

    private static final String SUSTENTO_TEST = "Corrección de historial importado de Excel";

    /** Papeleta de vacaciones realmente gozada (estado GOZADO, activa) con {@code dias} días. */
    private com.indeci.rrhh.entity.Vacacion papeletaGozada(int dias) {
        com.indeci.rrhh.entity.Vacacion v = new com.indeci.rrhh.entity.Vacacion();
        v.setDias((double) dias);
        v.setEstado("GOZADO");
        v.setActivo(1);
        return v;
    }

    @Test
    void recalcular_exige_sustento_no_vacio() {
        Long empleadoId = 299L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc = servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 0);

        assertThatThrownBy(() -> svc.recalcularProvisionManual(empleadoId, "   "))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("sustento");
        org.mockito.Mockito.verifyNoInteractions(planillaRepo, saldoRepo);
    }

    /**
     * Reproduce EXACTAMENTE el bug reportado: empleado con 5 meses de servicio (ingreso
     * 11/07/2026) con fila del Excel Ganados=60/Gozados=51 — normativamente imposible.
     * El récord real del período es 0 (período inválido) → la fila del Excel se anula
     * (activo=0, marcador con sustento) y NO se inserta reemplazo. El agregado activo → 0/0/0.
     */
    @Test
    void recalcular_anula_fila_invalida_sin_inflar_ni_insertar_reemplazo() {
        Long empleadoId = 300L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc = servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 0);

        LocalDate ingreso = LocalDate.of(2026, 7, 11);
        EmpleadoPlanilla vinculo = vinculoDeEmpleado(empleadoId, ingreso);
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(vinculo));

        com.indeci.rrhh.entity.VacacionSaldo filaExcel = new com.indeci.rrhh.entity.VacacionSaldo();
        filaExcel.setId(9001L);
        filaExcel.setEmpleadoId(empleadoId);
        filaExcel.setAnio(2026);
        filaExcel.setDiasGanados(60d);
        filaExcel.setDiasGozados(51d);
        filaExcel.setOrigen("MIGRACION_INICIAL_2026");
        filaExcel.setActivo(1);
        when(saldoRepo.findByEmpleadoIdAndActivo(empleadoId, 1)).thenReturn(List.of(filaExcel));

        com.indeci.rrhh.dto.RecalculoManualResultDto resultado =
                svc.recalcularProvisionManual(empleadoId, SUSTENTO_TEST);

        assertThat(resultado.cambios()).hasSize(1);
        com.indeci.rrhh.dto.CorreccionSaldoDto cambio = resultado.cambios().get(0);
        assertThat(cambio.anio()).isEqualTo(2026);
        assertThat(cambio.ganadosAnterior()).isEqualTo(60d);
        assertThat(cambio.tipo()).isEqualTo("ANULADO");

        assertThat(filaExcel.getActivo()).isZero();
        assertThat(filaExcel.getDiasGanados()).isEqualTo(60d); // histórico intacto, jamás DELETE
        assertThat(filaExcel.getObservacion()).contains("ANULADO_POR_RECALCULO").contains(SUSTENTO_TEST);
        org.mockito.Mockito.verify(saldoRepo).saveAndFlush(filaExcel);
        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.never()).save(any());
    }

    /**
     * CASO REPORTADO (AGUILAR SOTO): 1 año 2 meses de servicio, fila del Excel Ganados=30/
     * Gozados=30, PERO sin papeletas reales aprobadas → el "30 gozados" del Excel se descarta.
     * Tras recalcular: la fila 30/30 se anula y se inserta 30/0 (ganados=récord real, gozados
     * =papeletas=0). Estado activo resultante: Corresponden=30, Gozados=0, Saldo=30.
     */
    @Test
    void recalcular_descarta_gozados_del_excel_si_no_hay_papeletas() {
        Long empleadoId = 1690L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionRepository vacacionRepo = mock(VacacionRepository.class);
        VacacionProvisionService svc =
                servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, vacacionRepo, 0);

        LocalDate ingreso = LocalDate.now().minusYears(1).minusMonths(2); // 1 año 2 meses → 1 período válido
        EmpleadoPlanilla vinculo = vinculoDeEmpleado(empleadoId, ingreso);
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(vinculo));
        when(vacacionRepo.findByEmpleadoIdAndActivo(empleadoId, 1)).thenReturn(List.of()); // 0 papeletas

        com.indeci.rrhh.entity.VacacionSaldo filaExcel = new com.indeci.rrhh.entity.VacacionSaldo();
        filaExcel.setId(7001L);
        filaExcel.setEmpleadoId(empleadoId);
        filaExcel.setAnio(ingreso.getYear() + 1);
        filaExcel.setDiasGanados(30d);
        filaExcel.setDiasGozados(30d); // del Excel — NO respaldado por papeletas
        filaExcel.setActivo(1);
        when(saldoRepo.findByEmpleadoIdAndActivo(empleadoId, 1)).thenReturn(List.of(filaExcel));

        svc.recalcularProvisionManual(empleadoId, SUSTENTO_TEST);

        // La fila 30/30 se anula (gozados 30 ≠ 0 deseado).
        assertThat(filaExcel.getActivo()).isZero();
        org.mockito.Mockito.verify(saldoRepo).saveAndFlush(filaExcel);

        // Se inserta la fila limpia 30/0.
        org.mockito.ArgumentCaptor<com.indeci.rrhh.entity.VacacionSaldo> captor =
                org.mockito.ArgumentCaptor.forClass(com.indeci.rrhh.entity.VacacionSaldo.class);
        org.mockito.Mockito.verify(saldoRepo).save(captor.capture());
        com.indeci.rrhh.entity.VacacionSaldo nueva = captor.getValue();
        assertThat(nueva.getDiasGanados()).isEqualTo(30d);
        assertThat(nueva.getDiasGozados()).isEqualTo(0d); // gozados del Excel DESCARTADO
        assertThat(nueva.getOrigen()).isEqualTo("RECALCULO_SISTEMA");
    }

    /**
     * FIFO: goce real de 40 días de papeletas repartido sobre 2 períodos válidos (más antiguo
     * primero) → año+1 recibe 30 (tope), año+2 recibe 10. Los gozados salen de las papeletas,
     * NO de las filas del Excel.
     */
    @Test
    void recalcular_distribuye_gozado_real_fifo_sobre_periodos_validos() {
        Long empleadoId = 306L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionRepository vacacionRepo = mock(VacacionRepository.class);
        VacacionProvisionService svc =
                servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, vacacionRepo, 0);

        LocalDate ingreso = LocalDate.now().minusYears(2);
        EmpleadoPlanilla vinculo = vinculoDeEmpleado(empleadoId, ingreso);
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(vinculo));
        when(saldoRepo.findByEmpleadoIdAndActivo(empleadoId, 1)).thenReturn(List.of()); // sin filas previas
        // 40 días de goce real (p. ej. dos papeletas de 25 y 15).
        when(vacacionRepo.findByEmpleadoIdAndActivo(empleadoId, 1))
                .thenReturn(List.of(papeletaGozada(25), papeletaGozada(15)));

        svc.recalcularProvisionManual(empleadoId, SUSTENTO_TEST);

        org.mockito.ArgumentCaptor<com.indeci.rrhh.entity.VacacionSaldo> captor =
                org.mockito.ArgumentCaptor.forClass(com.indeci.rrhh.entity.VacacionSaldo.class);
        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.times(2)).save(captor.capture());

        com.indeci.rrhh.entity.VacacionSaldo anioAntiguo = captor.getAllValues().stream()
                .filter(v -> v.getAnio().equals(ingreso.getYear() + 1)).findFirst().orElseThrow();
        com.indeci.rrhh.entity.VacacionSaldo anioReciente = captor.getAllValues().stream()
                .filter(v -> v.getAnio().equals(ingreso.getYear() + 2)).findFirst().orElseThrow();
        assertThat(anioAntiguo.getDiasGozados()).isEqualTo(30d); // FIFO: el más antiguo se llena primero
        assertThat(anioReciente.getDiasGozados()).isEqualTo(10d); // remanente
    }

    @Test
    void recalcular_no_cambia_nada_si_ya_esta_correcto() {
        Long empleadoId = 301L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionRepository vacacionRepo = mock(VacacionRepository.class);
        VacacionProvisionService svc =
                servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, vacacionRepo, 0);

        LocalDate ingreso = LocalDate.now().minusYears(2);
        EmpleadoPlanilla vinculo = vinculoDeEmpleado(empleadoId, ingreso);
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(vinculo));
        // Goce real de 10 días → FIFO deja año+1 con gozados=10 (coincide con la fila existente).
        when(vacacionRepo.findByEmpleadoIdAndActivo(empleadoId, 1)).thenReturn(List.of(papeletaGozada(10)));

        com.indeci.rrhh.entity.VacacionSaldo filaOk = new com.indeci.rrhh.entity.VacacionSaldo();
        filaOk.setEmpleadoId(empleadoId);
        filaOk.setAnio(ingreso.getYear() + 1);
        filaOk.setDiasGanados(30d);
        filaOk.setDiasGozados(10d);
        when(saldoRepo.findByEmpleadoIdAndActivo(empleadoId, 1)).thenReturn(List.of(filaOk));

        com.indeci.rrhh.dto.RecalculoManualResultDto resultado =
                svc.recalcularProvisionManual(empleadoId, SUSTENTO_TEST);

        assertThat(resultado.sinCambios()).isEqualTo(1); // la fila año+1 coincide → no se toca
        assertThat(resultado.cambios()).hasSize(1); // el 2do período (año+2) aún falta: se crea
        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.never()).save(filaOk);
        assertThat(filaOk.getActivo()).isNull(); // nunca tocada
    }

    @Test
    void recalcular_crea_periodos_ya_cumplidos_que_no_tenian_fila() {
        Long empleadoId = 302L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc = servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 0);

        LocalDate ingreso = LocalDate.now().minusYears(2);
        EmpleadoPlanilla vinculo = vinculoDeEmpleado(empleadoId, ingreso);
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(vinculo));
        when(saldoRepo.findByEmpleadoIdAndActivo(empleadoId, 1)).thenReturn(List.of());

        com.indeci.rrhh.dto.RecalculoManualResultDto resultado =
                svc.recalcularProvisionManual(empleadoId, SUSTENTO_TEST);

        assertThat(resultado.cambios()).hasSize(2);
        // Sin papeletas → ambos períodos con gozados=0 (el Excel no aporta nada al recálculo).
        assertThat(resultado.cambios()).allMatch(c -> "CREADO".equals(c.tipo()) && c.ganadosNuevo() == 30d);
        org.mockito.ArgumentCaptor<com.indeci.rrhh.entity.VacacionSaldo> captor =
                org.mockito.ArgumentCaptor.forClass(com.indeci.rrhh.entity.VacacionSaldo.class);
        org.mockito.Mockito.verify(saldoRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(f -> "RECALCULO_SISTEMA".equals(f.getOrigen())
                && f.getDiasGozados() == 0d);
    }

    @Test
    void recalcular_trunca_el_sustento_largo_en_el_marcador_corto() {
        Long empleadoId = 303L;
        EmpleadoPlanillaRepository planillaRepo = mock(EmpleadoPlanillaRepository.class);
        VacacionSaldoRepository saldoRepo = mock(VacacionSaldoRepository.class);
        JornadaRegimenRepository jornadaRepo = mock(JornadaRegimenRepository.class);
        VacacionProvisionService svc = servicioConIncidenciaFija(planillaRepo, saldoRepo, jornadaRepo, 0);

        LocalDate ingreso = LocalDate.of(2026, 7, 11);
        EmpleadoPlanilla vinculo = vinculoDeEmpleado(empleadoId, ingreso);
        when(planillaRepo.findByEmpleadoIdInAndActivo(List.of(empleadoId), 1)).thenReturn(List.of(vinculo));

        com.indeci.rrhh.entity.VacacionSaldo filaExcel = new com.indeci.rrhh.entity.VacacionSaldo();
        filaExcel.setId(9002L);
        filaExcel.setEmpleadoId(empleadoId);
        filaExcel.setAnio(2026);
        filaExcel.setDiasGanados(60d);
        filaExcel.setDiasGozados(51d);
        when(saldoRepo.findByEmpleadoIdAndActivo(empleadoId, 1)).thenReturn(List.of(filaExcel));

        String sustentoLargo = "S".repeat(300);
        svc.recalcularProvisionManual(empleadoId, sustentoLargo);

        assertThat(filaExcel.getObservacion().length()).isLessThanOrEqualTo(200);
        assertThat(filaExcel.getObservacion()).endsWith("...");
    }
}
