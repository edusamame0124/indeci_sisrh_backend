package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.dto.PadronVacacionalPageDto;
import com.indeci.rrhh.dto.PadronVacacionalRowDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.dto.PersonaResumenPageDto;
import com.indeci.rrhh.entity.Cargo;
import com.indeci.rrhh.entity.Dependencia;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.CargoRepository;
import com.indeci.rrhh.repository.DependenciaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;

/**
 * SPEC_VACACIONES F4 — PadronVacacionalService. Mockea la base paginada + repos; usa
 * F1 (TiempoServicioService) y F3 (VacacionCalculoService) REALES. Aserciones robustas a
 * la fecha (relaciones, no valores absolutos que dependan de HOY).
 */
@ExtendWith(MockitoExtension.class)
class PadronVacacionalServiceTest {

    @Mock private PersonaService personaService;
    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private EmpleadoPuestoRepository empleadoPuestoRepository;
    @Mock private VacacionSaldoRepository vacacionSaldoRepository;
    @Mock private CargoRepository cargoRepository;
    @Mock private DependenciaRepository dependenciaRepository;
    @Mock private com.indeci.rrhh.repository.JornadaRegimenRepository jornadaRegimenRepository;
    @Mock private com.indeci.rrhh.service.incidencia.IncidenciaLaboralCompuesta incidenciaLaboralCompuesta;

    private PadronVacacionalService service;

    @BeforeEach
    void setUp() {
        service = new PadronVacacionalService(
                personaService,
                empleadoPlanillaRepository,
                empleadoPuestoRepository,
                vacacionSaldoRepository,
                cargoRepository,
                dependenciaRepository,
                new TiempoServicioService(empleadoPlanillaRepository), // F1 real (calcularDesde no toca repo)
                new VacacionCalculoService((empId, desde, hasta) -> 0), // F3 real
                jornadaRegimenRepository,
                incidenciaLaboralCompuesta);
    }

    private PersonaResumenDto persona(long id, long empId, String nombre, String dni, String regimen) {
        return new PersonaResumenDto(id, empId, nombre, dni, "COD" + id, "ACTIVO", regimen, null, null, null, null);
    }

    @Test
    void consultar_arma_padron_con_computo_y_mapeo_de_catalogos() {
        // Base paginada (server-side) con 2 empleados: 42 (con vínculo) y 99 (sin vínculo).
        when(personaService.listarPaginado("ana", 0, 25)).thenReturn(new PersonaResumenPageDto(
                List.of(
                        persona(1, 42, "ANA SALAS", "44552584", "CAS"),
                        persona(2, 99, "BETO DIAZ", "10306222", "276")),
                2L, 1, 0, 25));

        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setEmpleadoId(42L);
        v.setActivo(1);
        v.setFechaInicioContrato(LocalDate.of(2018, 12, 14));
        v.setSueldoBasico(2364.19);
        when(empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(v));

        EmpleadoPuesto p = new EmpleadoPuesto();
        p.setEmpleadoId(42L);
        p.setCargoId(5L);
        p.setDependenciaId(7L);
        when(empleadoPuestoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(p));

        VacacionSaldo s42 = new VacacionSaldo();
        s42.setEmpleadoId(42L);
        s42.setDiasGozados(180.0);
        s42.setDiasGanados(210.0);
        VacacionSaldo s99 = new VacacionSaldo();
        s99.setEmpleadoId(99L);
        s99.setDiasGozados(12.0);
        s99.setDiasGanados(0.0);
        when(vacacionSaldoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(s42, s99));

        Cargo c = new Cargo();
        c.setId(5L);
        c.setNombre("ANALISTA");
        when(cargoRepository.findAll()).thenReturn(List.of(c));

        Dependencia d = new Dependencia();
        d.setId(7L);
        d.setNombre("OTIC");
        when(dependenciaRepository.findAll()).thenReturn(List.of(d));

        // Sin incidencias → días efectivos = tiempo de servicio (cero regresión).
        when(incidenciaLaboralCompuesta.calcularDesglose(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.indeci.rrhh.dto.DiasNoComputablesDto.cero());

        PadronVacacionalPageDto out = service.consultar("ana", 0, 25);

        assertThat(out.totalElements()).isEqualTo(2L);
        assertThat(out.content()).hasSize(2);

        // Fila con vínculo (emp 42).
        PadronVacacionalRowDto a = out.content().get(0);
        assertThat(a.dni()).isEqualTo("44552584");
        assertThat(a.nombreCompleto()).isEqualTo("ANA SALAS");
        assertThat(a.regimenLaboral()).isEqualTo("CAS");
        assertThat(a.cargo()).isEqualTo("ANALISTA");
        assertThat(a.dependencia()).isEqualTo("OTIC");
        assertThat(a.sinVinculo()).isFalse();
        assertThat(a.diasGozados()).isEqualTo(180.0);
        // Relaciones invariantes a la fecha de corte:
        assertThat(a.diasCorresponden()).isEqualTo(a.aniosServicio() * 30);
        assertThat(a.saldo()).isEqualTo(a.diasCorresponden() - 180.0);
        assertThat(a.estadoRecord()).isNotBlank();
        // Sin incidencias: LSG y faltas en 0.
        assertThat(a.diasNoComputablesLsg()).isZero();
        assertThat(a.diasNoComputablesFaltas()).isZero();
        // Refactor Récord Anual Estricto: "Efectivos" es el ÚLTIMO AÑO de servicio completado
        // (no la carrera entera) — sin incidencias es exactamente 1 año (360/360/0), sin
        // importar cuántos años de antigüedad total tenga el empleado (7 en este fixture).
        assertThat(a.aniosEfectivos()).isEqualTo(1);
        assertThat(a.mesesEfectivos()).isEqualTo(0);
        assertThat(a.diasEfectivos()).isEqualTo(0);

        // Fila sin vínculo (emp 99): corresponden 0, gozados passthrough, saldo negativo.
        PadronVacacionalRowDto b = out.content().get(1);
        assertThat(b.sinVinculo()).isTrue();
        assertThat(b.diasCorresponden()).isZero();
        assertThat(b.diasGozados()).isEqualTo(12.0);
        assertThat(b.saldo()).isEqualTo(-12.0);
    }

    @Test
    void consultar_descuenta_lsg_y_faltas_del_record() {
        when(personaService.listarPaginado("x", 0, 25)).thenReturn(new PersonaResumenPageDto(
                List.of(persona(1, 50, "CARLA RIOS", "70011223", "276")), 1L, 1, 0, 25));

        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setEmpleadoId(50L);
        v.setActivo(1);
        v.setFechaInicioContrato(LocalDate.now().minusMonths(8)); // ~240 días 30/360, ts.anios()==0
        v.setSueldoBasico(1200.0);
        when(empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(v));
        when(empleadoPuestoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of());
        when(vacacionSaldoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of());
        when(cargoRepository.findAll()).thenReturn(List.of());
        when(dependenciaRepository.findAll()).thenReturn(List.of());
        // LSG 40 + faltas 15 = 55 no computables → efectivos ~185, bajo el umbral 210 (jornada 5).
        when(incidenciaLaboralCompuesta.calcularDesglose(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.indeci.rrhh.dto.DiasNoComputablesDto.of(40, 15));

        PadronVacacionalRowDto r = service.consultar("x", 0, 25).content().get(0);

        assertThat(r.diasNoComputablesLsg()).isEqualTo(40);
        assertThat(r.diasNoComputablesFaltas()).isEqualTo(15);
        // Días efectivos < servicio bruto (se descontaron las incidencias).
        int efectivos = r.aniosEfectivos() * 360 + r.mesesEfectivos() * 30 + r.diasEfectivos();
        int servicio = r.aniosServicio() * 360 + r.mesesServicio() * 30 + r.diasServicio();
        assertThat(efectivos).isEqualTo(servicio - 55);
        // Refactor Récord Anual Estricto: primer año de servicio aún no completado (ts.anios()==0)
        // → estado transicional EN_ACUMULACION, no OK/SIN_RECORD_LEGAL (esos aplican recién al
        // evaluar un año YA completado).
        assertThat(r.estadoRecord()).isEqualTo("EN_ACUMULACION");
    }

    /**
     * FUENTE ÚNICA DE VERDAD: "Corresponden" y "Saldo" se LEEN de la BD (suma de filas
     * ACTIVAS), NO se calculan al vuelo (años×30). Empleado de 5 meses con una fila activa
     * de ganados=0 / gozados=51 (estado base del dato inconsistente, aún no anulado) → el
     * Padrón expone exactamente 0 / 51 / -51 tal cual está en Oracle, sin inflar ni inventar.
     */
    @Test
    void consultar_lee_corresponden_y_saldo_desde_la_bd_sin_formula() {
        when(personaService.listarPaginado("abad", 0, 25)).thenReturn(new PersonaResumenPageDto(
                List.of(persona(1, 1690, "ABAD GIRON EVER", "41868447", "1057")), 1L, 1, 0, 25));

        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setEmpleadoId(1690L);
        v.setActivo(1);
        v.setFechaInicioContrato(LocalDate.now().minusMonths(5)); // 5 meses → ts.anios()==0
        v.setSueldoBasico(2364.19);
        when(empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(v));
        when(empleadoPuestoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of());
        when(cargoRepository.findAll()).thenReturn(List.of());
        when(dependenciaRepository.findAll()).thenReturn(List.of());
        when(incidenciaLaboralCompuesta.calcularDesglose(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.indeci.rrhh.dto.DiasNoComputablesDto.cero());

        // Estado base leído de Oracle: 0 ganados, 51 gozados (dato inconsistente, activo=1).
        VacacionSaldo s = new VacacionSaldo();
        s.setEmpleadoId(1690L);
        s.setAnio(2026);
        s.setDiasGanados(0.0);
        s.setDiasGozados(51.0);
        when(vacacionSaldoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(s));

        PadronVacacionalRowDto r = service.consultar("abad", 0, 25).content().get(0);

        assertThat(r.diasCorresponden()).isZero();   // leído de BD, NO calculado por años×30
        assertThat(r.diasGozados()).isEqualTo(51.0);
        assertThat(r.saldo()).isEqualTo(-51.0);       // realidad matemática: 0 - 51
        assertThat(r.estadoRecord()).isEqualTo("EN_ACUMULACION");
    }

    /**
     * Refactor Récord Anual Estricto — certifica que el bloqueo YA NO se diluye por antigüedad
     * acumulada. Antes del fix: ts.totalDias360() de 3 años (~1080 días) − 250 días no
     * computables ≈ 830 efectivos, muy por encima de 210/260 → SIEMPRE mostraba "OK" sin
     * importar el récord real del último año. Con el fix, se evalúa SOLO el último año de
     * servicio completado: 360 − 250 = 110 efectivos < 210 (jornada 5) → SIN récord legal.
     */
    @Test
    void consultar_empleado_veterano_con_incidencias_concentradas_en_ultimo_anio_bloquea() {
        when(personaService.listarPaginado("y", 0, 25)).thenReturn(new PersonaResumenPageDto(
                List.of(persona(1, 60, "JOSE PEREZ", "70099887", "276")), 1L, 1, 0, 25));

        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setEmpleadoId(60L);
        v.setActivo(1);
        v.setFechaInicioContrato(LocalDate.now().minusYears(3)); // veterano: ts.anios() >= 1
        v.setSueldoBasico(1500.0);
        when(empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(v));
        when(empleadoPuestoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of());
        when(vacacionSaldoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of());
        when(cargoRepository.findAll()).thenReturn(List.of());
        when(dependenciaRepository.findAll()).thenReturn(List.of());
        when(incidenciaLaboralCompuesta.calcularDesglose(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.indeci.rrhh.dto.DiasNoComputablesDto.of(250, 0));

        PadronVacacionalRowDto r = service.consultar("y", 0, 25).content().get(0);

        assertThat(r.aniosServicio()).isGreaterThanOrEqualTo(2); // antigüedad real, varios años
        assertThat(r.estadoRecord()).isEqualTo(com.indeci.rrhh.dto.VacacionCalculoDto.RECORD_SIN);
    }

    /**
     * F9.3 — D.S. 013-2019-PCM: 3 años con saldo pendiente de gozar supera el tope de 2 →
     * requiereDecisionAcumulacion=true (solo marca para evaluación de RR.HH.; NUNCA bloquea
     * ni pierde saldo automáticamente).
     */
    @Test
    void consultar_marca_requiere_decision_cuando_supera_el_tope_de_periodos_acumulados() {
        when(personaService.listarPaginado("z", 0, 25)).thenReturn(new PersonaResumenPageDto(
                List.of(persona(1, 70, "LUIS TORRES", "70055443", "276")), 1L, 1, 0, 25));

        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setEmpleadoId(70L);
        v.setActivo(1);
        v.setFechaInicioContrato(LocalDate.now().minusYears(4));
        v.setSueldoBasico(1500.0);
        when(empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(v));
        when(empleadoPuestoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of());
        when(cargoRepository.findAll()).thenReturn(List.of());
        when(dependenciaRepository.findAll()).thenReturn(List.of());
        when(incidenciaLaboralCompuesta.calcularDesglose(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.indeci.rrhh.dto.DiasNoComputablesDto.cero());

        VacacionSaldo s2023 = new VacacionSaldo();
        s2023.setEmpleadoId(70L);
        s2023.setAnio(2023);
        s2023.setDiasGanados(30.0);
        s2023.setDiasGozados(0.0);
        VacacionSaldo s2024 = new VacacionSaldo();
        s2024.setEmpleadoId(70L);
        s2024.setAnio(2024);
        s2024.setDiasGanados(30.0);
        s2024.setDiasGozados(0.0);
        VacacionSaldo s2025 = new VacacionSaldo();
        s2025.setEmpleadoId(70L);
        s2025.setAnio(2025);
        s2025.setDiasGanados(30.0);
        s2025.setDiasGozados(10.0); // parcial: sigue pendiente
        when(vacacionSaldoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(s2023, s2024, s2025));

        PadronVacacionalRowDto r = service.consultar("z", 0, 25).content().get(0);

        assertThat(r.periodosAcumuladosSinGozar()).isEqualTo(3);
        assertThat(r.requiereDecisionAcumulacion()).isTrue();
    }

    @Test
    void consultar_no_marca_requiere_decision_dentro_del_tope() {
        when(personaService.listarPaginado("w", 0, 25)).thenReturn(new PersonaResumenPageDto(
                List.of(persona(1, 71, "MARIA LOPEZ", "70044332", "276")), 1L, 1, 0, 25));

        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setEmpleadoId(71L);
        v.setActivo(1);
        v.setFechaInicioContrato(LocalDate.now().minusYears(3));
        v.setSueldoBasico(1500.0);
        when(empleadoPlanillaRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(v));
        when(empleadoPuestoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of());
        when(cargoRepository.findAll()).thenReturn(List.of());
        when(dependenciaRepository.findAll()).thenReturn(List.of());
        when(incidenciaLaboralCompuesta.calcularDesglose(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.indeci.rrhh.dto.DiasNoComputablesDto.cero());

        VacacionSaldo s2025 = new VacacionSaldo();
        s2025.setEmpleadoId(71L);
        s2025.setAnio(2025);
        s2025.setDiasGanados(30.0);
        s2025.setDiasGozados(0.0);
        when(vacacionSaldoRepository.findByEmpleadoIdInAndActivo(anyList(), eq(1)))
                .thenReturn(List.of(s2025));

        PadronVacacionalRowDto r = service.consultar("w", 0, 25).content().get(0);

        assertThat(r.periodosAcumuladosSinGozar()).isEqualTo(1);
        assertThat(r.requiereDecisionAcumulacion()).isFalse();
    }

    @Test
    void consultar_pagina_vacia_no_falla() {
        when(personaService.listarPaginado(null, 0, 25))
                .thenReturn(new PersonaResumenPageDto(List.of(), 0L, 0, 0, 25));

        PadronVacacionalPageDto out = service.consultar(null, 0, 25);

        assertThat(out.content()).isEmpty();
        assertThat(out.totalElements()).isZero();
    }
}
