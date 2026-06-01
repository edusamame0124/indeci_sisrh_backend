package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import com.indeci.rrhh.dto.PreflightValidacionDto;
import com.indeci.rrhh.dto.ValidacionHallazgoDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;

/**
 * F3.3c — Tests del Centro de Validaciones (preflight).
 *
 * <p>Cubre: validación de entrada, happy path, BLOQUEO, ALERTA, INFO,
 * combinación V6+V7 y empty period.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidacionPreflightServiceTest {

    @Mock private PeriodoPlanillaRepository periodoRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private EmpleadoPensionRepository pensionRepository;
    @Mock private EmpleadoConceptoRepository empleadoConceptoRepository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;
    @Mock private AsistenciaCabeceraRepository asistenciaRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private EmpleadoEventoRepository empleadoEventoRepository;
    @Mock private TipoEventoRepository tipoEventoRepository;

    @InjectMocks private ValidacionPreflightService service;

    private static final String PERIODO = "2026-05";

    // ================== Validación entrada ==================

    @Test
    void periodo_nulo_lanza_negocio() {
        assertThatThrownBy(() -> service.evaluar(null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("período");
        assertThatThrownBy(() -> service.evaluar(""))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void periodo_inexistente_devuelve_V1_y_corta_evaluacion() {
        when(periodoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(Optional.empty());

        PreflightValidacionDto r = service.evaluar(PERIODO);

        assertThat(r.totalBloqueos()).isEqualTo(1);
        assertThat(r.hallazgos()).hasSize(1);
        ValidacionHallazgoDto h = r.hallazgos().get(0);
        assertThat(h.codigo()).isEqualTo("V1");
        assertThat(h.severidad()).isEqualTo("BLOQUEO");
        assertThat(h.modulo()).isEqualTo("Período");
    }

    // ================== Happy path ==================

    @Test
    void happy_path_sin_hallazgos_devuelve_0_bloqueos() {
        configurarPeriodoOk();
        configurarEmpleadoOk(1L, "728", "Pérez Juan");
        configurarAsistenciaValidada(1L);
        cargarConceptos(); // catálogo sano
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(1L, 1))
                .thenReturn(List.of());
        when(empleadoEventoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of());

        PreflightValidacionDto r = service.evaluar(PERIODO);

        assertThat(r.totalBloqueos()).isZero();
        assertThat(r.totalAlertas()).isZero();
        assertThat(r.totalInfo()).isZero();
    }

    // ================== V2 BLOQUEO: cert presupuestal ==================

    @Test
    void v2_falta_certificacion_presupuestal_es_bloqueo() {
        PeriodoPlanilla p = new PeriodoPlanilla();
        p.setId(99L);
        p.setPeriodo(PERIODO);
        p.setActivo(1);
        p.setEstado("ABIERTO");
        p.setNroCertPresup(null);
        when(periodoRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(Optional.of(p));
        when(empleadoRepository.findByEstado("ACTIVO")).thenReturn(List.of());
        when(planillaRepository.findByActivo(1)).thenReturn(List.of());
        when(regimenLaboralRepository.findAll()).thenReturn(List.of());
        when(asistenciaRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of(asistenciaValidada(1L)));
        when(conceptoRepository.findByActivo(1)).thenReturn(List.of());
        when(tipoEventoRepository.findAll()).thenReturn(List.of());
        when(empleadoEventoRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of());

        PreflightValidacionDto r = service.evaluar(PERIODO);

        assertThat(r.hallazgos())
                .extracting(ValidacionHallazgoDto::codigo)
                .contains("V2");
    }

    // ================== V3 BLOQUEO: asistencia no validada ==================

    @Test
    void v3_asistencia_no_validada_es_bloqueo() {
        configurarPeriodoOk();
        configurarEmpleadoOk(1L, "728", "Pérez Juan");
        AsistenciaCabecera a = new AsistenciaCabecera();
        a.setEmpleadoId(1L);
        a.setPeriodo(PERIODO);
        a.setActivo(1);
        a.setEstado("PENDIENTE");
        when(asistenciaRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of(a));
        cargarConceptos();
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(1L, 1)).thenReturn(List.of());
        when(empleadoEventoRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of());

        PreflightValidacionDto r = service.evaluar(PERIODO);

        assertThat(r.hallazgos())
                .extracting(ValidacionHallazgoDto::codigo)
                .contains("V3");
    }

    // ================== V5 ALERTA: sin régimen pensionario ==================

    @Test
    void v5_empleado_sin_pension_es_alerta() {
        configurarPeriodoOk();
        configurarEmpleadoOk(1L, "728", "Pérez Juan");
        when(pensionRepository.existsByEmpleadoIdAndActivo(1L, 1)).thenReturn(false);
        configurarAsistenciaValidada(1L);
        cargarConceptos();
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(1L, 1)).thenReturn(List.of());
        when(empleadoEventoRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of());

        PreflightValidacionDto r = service.evaluar(PERIODO);

        assertThat(r.totalAlertas()).isPositive();
        assertThat(r.hallazgos())
                .extracting(ValidacionHallazgoDto::codigo)
                .contains("V5");
    }

    // ================== V6 + V7: concepto sin MEF + régimen incompatible ==================

    @Test
    void v6_y_v7_simultaneos_para_el_mismo_concepto_emp() {
        configurarPeriodoOk();
        configurarEmpleadoOk(1L, "276", "Pérez Juan");
        configurarAsistenciaValidada(1L);

        // Concepto activo: SIN codigoMef + régimen aplicable=728
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(500L);
        c.setNombre("DS pacto colectivo");
        c.setActivo(1);
        c.setCodigoMef(null);
        c.setRegimenAplicable("728");
        c.setTipoConcepto("REMUNERATIVO");
        when(conceptoRepository.findByActivo(1)).thenReturn(List.of(c));

        EmpleadoConcepto ec = new EmpleadoConcepto();
        ec.setId(700L);
        ec.setEmpleadoId(1L);
        ec.setConceptoPlanillaId(500L);
        ec.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(1L, 1)).thenReturn(List.of(ec));

        when(tipoEventoRepository.findAll()).thenReturn(List.of());
        when(empleadoEventoRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of());

        PreflightValidacionDto r = service.evaluar(PERIODO);

        assertThat(r.hallazgos())
                .extracting(ValidacionHallazgoDto::codigo)
                .contains("V6", "V7");
        assertThat(r.totalBloqueos()).isGreaterThanOrEqualTo(2);
    }

    // ================== V8 INFO: empleado en transición ==================

    @Test
    void v8_estado_laboral_en_transicion_es_info() {
        configurarPeriodoOk();
        configurarEmpleadoConTransicion();
        configurarAsistenciaValidada(1L);
        cargarConceptos();
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(1L, 1)).thenReturn(List.of());
        when(empleadoEventoRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of());

        PreflightValidacionDto r = service.evaluar(PERIODO);

        assertThat(r.totalInfo()).isPositive();
        assertThat(r.hallazgos())
                .extracting(ValidacionHallazgoDto::codigo)
                .contains("V8");
    }

    // ================== Helpers ==================

    private void configurarPeriodoOk() {
        PeriodoPlanilla p = new PeriodoPlanilla();
        p.setId(99L);
        p.setPeriodo(PERIODO);
        p.setActivo(1);
        p.setEstado("ABIERTO");
        p.setNroCertPresup("CERT-2026-05");
        when(periodoRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(Optional.of(p));
    }

    private void configurarEmpleadoOk(Long empId, String regimenCodigo, String nombre) {
        Empleado e = new Empleado();
        e.setId(empId);
        e.setPersonaId(empId);
        e.setEstado("ACTIVO");
        when(empleadoRepository.findByEstado("ACTIVO")).thenReturn(List.of(e));

        Persona p = new Persona();
        p.setId(empId);
        p.setDni("0000000" + empId);
        p.setNombreCompleto(nombre);
        // setear el nombre completo vía la otra firma si existiera (mejor mockear getNombreCompleto)
        when(personaRepository.findById(empId)).thenReturn(Optional.of(p));

        EmpleadoPlanilla pl = new EmpleadoPlanilla();
        pl.setId(empId);
        pl.setEmpleadoId(empId);
        pl.setActivo(1);
        pl.setRegimenLaboralId(1L);
        pl.setEstadoLaboral("ACTIVO");
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(pl));

        RegimenLaboral rl = new RegimenLaboral();
        rl.setId(1L);
        rl.setCodigo(regimenCodigo);
        when(regimenLaboralRepository.findAll()).thenReturn(List.of(rl));

        when(pensionRepository.existsByEmpleadoIdAndActivo(empId, 1)).thenReturn(true);
        when(empleadoRepository.findById(empId)).thenReturn(Optional.of(e));
    }

    private void configurarEmpleadoConTransicion() {
        Empleado e = new Empleado();
        e.setId(1L);
        e.setPersonaId(1L);
        e.setEstado("ACTIVO");
        when(empleadoRepository.findByEstado("ACTIVO")).thenReturn(List.of(e));
        Persona p = new Persona();
        p.setId(1L);
        when(personaRepository.findById(1L)).thenReturn(Optional.of(p));

        EmpleadoPlanilla pl = new EmpleadoPlanilla();
        pl.setId(1L);
        pl.setEmpleadoId(1L);
        pl.setActivo(1);
        pl.setRegimenLaboralId(1L);
        pl.setEstadoLaboral("EN_TRANSICION");
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(pl));

        RegimenLaboral rl = new RegimenLaboral();
        rl.setId(1L);
        rl.setCodigo("CAS");
        when(regimenLaboralRepository.findAll()).thenReturn(List.of(rl));

        when(pensionRepository.existsByEmpleadoIdAndActivo(1L, 1)).thenReturn(true);
    }

    private void configurarAsistenciaValidada(Long empId) {
        when(asistenciaRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of(asistenciaValidada(empId)));
    }

    private AsistenciaCabecera asistenciaValidada(Long empId) {
        AsistenciaCabecera a = new AsistenciaCabecera();
        a.setEmpleadoId(empId);
        a.setPeriodo(PERIODO);
        a.setActivo(1);
        a.setEstado("VALIDADA");
        return a;
    }

    /** Cataloga conceptos sin problemas (no genera V9). */
    private void cargarConceptos() {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(100L);
        c.setNombre("Sueldo básico");
        c.setActivo(1);
        c.setCodigoMef("00001");
        c.setTipoConcepto("REMUNERATIVO");
        c.setRegimenAplicable("TODOS");
        when(conceptoRepository.findByActivo(1)).thenReturn(List.of(c));
        when(tipoEventoRepository.findAll()).thenReturn(List.<TipoEvento>of());
    }

    /** Usado por algunos tests que necesitan un evento con adjunto faltante. */
    @SuppressWarnings("unused")
    private EmpleadoEvento eventoSinAdjunto(Long empId, Long tipoId) {
        EmpleadoEvento ev = new EmpleadoEvento();
        ev.setId(800L);
        ev.setEmpleadoId(empId);
        ev.setTipoEventoId(tipoId);
        ev.setPeriodo(PERIODO);
        ev.setActivo(1);
        ev.setSustentoLegajoDocId(null);
        return ev;
    }
}
