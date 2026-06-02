package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PreflightValidacionDto;
import com.indeci.rrhh.dto.RecalculoCriterioDto;
import com.indeci.rrhh.dto.RecalculoPreviewDto;
import com.indeci.rrhh.dto.RecalculoResultadoDto;
import com.indeci.rrhh.dto.ValidacionHallazgoDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

/**
 * F3.4c — Tests del Asistente de Recálculo.
 *
 * <p>Cubre: criterio TODOS / REGIMEN_LABORAL / EMPLEADOS_LISTA /
 * CON_PREFLIGHT_PENDIENTE; captura de delta tras ejecutar; manejo de
 * errores sin abortar el lote.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecalculoAsistenteServiceTest {

    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private ValidacionPreflightService preflightService;
    @Mock private GeneradorPlanillaService generadorService;

    @InjectMocks private RecalculoAsistenteService service;

    private static final String PERIODO = "2026-05";

    /** Lista mutable compartida entre helpers — cada empleado se acumula. */
    private final List<EmpleadoPlanilla> planillasActivas = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        // El campo está marcado @Lazy + @Autowired (no @InjectMocks).
        Field f = RecalculoAsistenteService.class.getDeclaredField("generadorService");
        f.setAccessible(true);
        f.set(service, generadorService);

        planillasActivas.clear();
        lenient().when(planillaRepository.findByActivo(1)).thenReturn(planillasActivas);
    }

    // ================== Validación de entrada ==================

    @Test
    void periodo_nulo_lanza() {
        assertThatThrownBy(() ->
                service.preview(null, new RecalculoCriterioDto("TODOS", null, null)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("período");
    }

    @Test
    void criterio_invalido_lanza() {
        assertThatThrownBy(() ->
                service.preview(PERIODO, new RecalculoCriterioDto("XX", null, null)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("criterio");
    }

    // ================== TODOS ==================

    @Test
    void preview_TODOS_devuelve_planillas_activas() {
        configurarBaseRegimenes();
        configurarEmpleado(10L, 1L, "Pérez Juan");
        configurarEmpleado(11L, 1L, "Soto María");

        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(10L, PERIODO, 1))
                .thenReturn(Optional.of(mov(10L, 2500.0)));
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(11L, PERIODO, 1))
                .thenReturn(Optional.empty());

        RecalculoPreviewDto r = service.preview(
                PERIODO, new RecalculoCriterioDto("TODOS", null, null));

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.items()).extracting("empleadoId").containsExactly(10L, 11L);
        assertThat(r.items().get(0).netoActual().doubleValue()).isEqualTo(2500.0);
        assertThat(r.items().get(0).tieneMovimientoPrevio()).isTrue();
        assertThat(r.items().get(1).tieneMovimientoPrevio()).isFalse();
    }

    // ================== REGIMEN_LABORAL ==================

    @Test
    void preview_REGIMEN_LABORAL_filtra_por_codigo() {
        // Régimen 728 → id 1; régimen CAS → id 2.
        RegimenLaboral r728 = regimen(1L, "728");
        RegimenLaboral rCas = regimen(2L, "CAS");
        when(regimenLaboralRepository.findAll()).thenReturn(List.of(r728, rCas));

        EmpleadoPlanilla pl1 = planilla(10L, 1L);
        EmpleadoPlanilla pl2 = planilla(11L, 2L);
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(pl1, pl2));

        configurarEmpleadoPersona(10L, "Pérez Juan");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(10L, PERIODO, 1))
                .thenReturn(Optional.empty());

        RecalculoPreviewDto r = service.preview(
                PERIODO, new RecalculoCriterioDto("REGIMEN_LABORAL", "728", null));

        assertThat(r.total()).isEqualTo(1);
        assertThat(r.items().get(0).empleadoId()).isEqualTo(10L);
        assertThat(r.items().get(0).regimenLaboralCodigo()).isEqualTo("728");
    }

    @Test
    void preview_REGIMEN_LABORAL_sin_valor_lanza() {
        assertThatThrownBy(() -> service.preview(
                PERIODO, new RecalculoCriterioDto("REGIMEN_LABORAL", "  ", null)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("régimen");
    }

    // ================== EMPLEADOS_LISTA ==================

    @Test
    void preview_EMPLEADOS_LISTA_filtra_solo_activos() {
        configurarBaseRegimenes();
        // Planilla activa solo para 10 y 12.
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                planilla(10L, 1L), planilla(12L, 1L)));

        configurarEmpleadoPersona(10L, "Pérez Juan");
        configurarEmpleadoPersona(12L, "García Luis");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(10L, PERIODO, 1))
                .thenReturn(Optional.empty());
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(12L, PERIODO, 1))
                .thenReturn(Optional.empty());

        // El usuario pidió 10, 11 (inactivo), 12.
        RecalculoPreviewDto r = service.preview(
                PERIODO,
                new RecalculoCriterioDto("EMPLEADOS_LISTA", null, List.of(10L, 11L, 12L)));

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.items()).extracting("empleadoId").containsExactly(10L, 12L);
    }

    // ================== CON_PREFLIGHT_PENDIENTE ==================

    @Test
    void preview_CON_PREFLIGHT_PENDIENTE_usa_hallazgos_BLOQUEO_y_ALERTA() {
        configurarBaseRegimenes();
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(
                planilla(10L, 1L), planilla(11L, 1L), planilla(12L, 1L)));
        configurarEmpleadoPersona(10L, "Pérez Juan");
        configurarEmpleadoPersona(11L, "Soto María");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(10L, PERIODO, 1))
                .thenReturn(Optional.empty());
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(11L, PERIODO, 1))
                .thenReturn(Optional.empty());

        PreflightValidacionDto pre = new PreflightValidacionDto(
                PERIODO, 1, 1, 1,
                List.of(
                        ValidacionHallazgoDto.bloqueo("V6", "Concepto", "x",
                                10L, "Pérez Juan", null),
                        ValidacionHallazgoDto.alerta("V5", "Empleado", "x",
                                11L, "Soto María", null),
                        // Info se descarta (no implica acción).
                        ValidacionHallazgoDto.info("V8", "Empleado", "x",
                                12L, "García Luis", null),
                        // Sin empleadoId — se descarta.
                        ValidacionHallazgoDto.bloqueo("V1", "Período", "x",
                                null, null, null)));
        when(preflightService.evaluar(PERIODO)).thenReturn(pre);

        RecalculoPreviewDto r = service.preview(
                PERIODO,
                new RecalculoCriterioDto("CON_PREFLIGHT_PENDIENTE", null, null));

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.items()).extracting("empleadoId")
                .containsExactlyInAnyOrder(10L, 11L);
    }

    // ================== EJECUTAR captura delta ==================

    @Test
    void ejecutar_invoca_generador_y_captura_delta() {
        configurarBaseRegimenes();
        configurarEmpleado(10L, 1L, "Pérez Juan");

        // Pre-call: neto 2500. Post-call: neto 2700.
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(10L, PERIODO, 1))
                .thenReturn(Optional.of(mov(10L, 2500.0)))
                .thenReturn(Optional.of(mov(10L, 2700.0)));
        doNothing().when(generadorService).generar(10L, PERIODO);

        RecalculoResultadoDto r = service.ejecutar(
                PERIODO, new RecalculoCriterioDto("TODOS", null, null));

        assertThat(r.total()).isEqualTo(1);
        assertThat(r.exitosos()).isEqualTo(1);
        assertThat(r.fallidos()).isZero();
        assertThat(r.totalDelta().doubleValue()).isEqualTo(200.0);
        assertThat(r.items().get(0).status()).isEqualTo("OK");
        assertThat(r.items().get(0).delta().doubleValue()).isEqualTo(200.0);
        verify(generadorService, times(1)).generar(10L, PERIODO);
    }

    // ================== EJECUTAR no aborta cuando 1 falla ==================

    @Test
    void ejecutar_un_empleado_falla_pero_continua_con_el_resto() {
        configurarBaseRegimenes();
        configurarEmpleado(10L, 1L, "Pérez Juan");
        configurarEmpleado(11L, 1L, "Soto María");

        // 10 falla. 11 va bien.
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(10L, PERIODO, 1))
                .thenReturn(Optional.of(mov(10L, 2500.0)));
        doThrow(new NegocioException("Falta cert presupuestal"))
                .when(generadorService).generar(eq(10L), eq(PERIODO));

        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(11L, PERIODO, 1))
                .thenReturn(Optional.of(mov(11L, 1800.0)))
                .thenReturn(Optional.of(mov(11L, 1900.0)));
        doNothing().when(generadorService).generar(eq(11L), eq(PERIODO));

        RecalculoResultadoDto r = service.ejecutar(
                PERIODO, new RecalculoCriterioDto("TODOS", null, null));

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.exitosos()).isEqualTo(1);
        assertThat(r.fallidos()).isEqualTo(1);
        assertThat(r.totalDelta().doubleValue()).isEqualTo(100.0);
        assertThat(r.items().get(0).status()).isEqualTo("ERROR");
        assertThat(r.items().get(0).razon()).contains("cert");
        assertThat(r.items().get(1).status()).isEqualTo("OK");
        assertThat(r.items().get(1).delta().doubleValue()).isEqualTo(100.0);
    }

    // ============================ HELPERS ============================

    private void configurarBaseRegimenes() {
        lenient().when(regimenLaboralRepository.findAll())
                .thenReturn(List.of(regimen(1L, "728"), regimen(2L, "CAS")));
    }

    private void configurarEmpleado(Long id, Long regimenId, String nombre) {
        planillasActivas.add(planilla(id, regimenId));
        configurarEmpleadoPersona(id, nombre);
    }

    /**
     * Versión utilitaria que NO toca {@code findByActivo}; pensada para tests
     * que ya configuraron la lista de planillas con {@code thenReturn}.
     */
    private void configurarEmpleadoPersona(Long id, String nombre) {
        Empleado e = new Empleado();
        e.setId(id);
        e.setPersonaId(id);
        e.setEstado("ACTIVO");
        lenient().when(empleadoRepository.findById(id)).thenReturn(Optional.of(e));

        Persona p = new Persona();
        p.setId(id);
        p.setNombreCompleto(nombre);
        lenient().when(personaRepository.findById(id)).thenReturn(Optional.of(p));
    }

    private EmpleadoPlanilla planilla(Long empleadoId, Long regimenId) {
        EmpleadoPlanilla pl = new EmpleadoPlanilla();
        pl.setId(empleadoId);
        pl.setEmpleadoId(empleadoId);
        pl.setActivo(1);
        pl.setRegimenLaboralId(regimenId);
        return pl;
    }

    private RegimenLaboral regimen(Long id, String codigo) {
        RegimenLaboral r = new RegimenLaboral();
        r.setId(id);
        r.setCodigo(codigo);
        r.setActivo(1);
        return r;
    }

    private MovimientoPlanilla mov(Long empleadoId, Double netoPagar) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setEmpleadoId(empleadoId);
        m.setPeriodo(PERIODO);
        m.setActivo(1);
        m.setNetoPagar(netoPagar);
        return m;
    }
}
