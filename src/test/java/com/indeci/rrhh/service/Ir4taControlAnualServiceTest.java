package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.Suspension4taVigenteDto;
import com.indeci.rrhh.dto.ir4ta.Ir4taReinicioInputDto;
import com.indeci.rrhh.entity.Ir4taConfigAnual;
import com.indeci.rrhh.entity.Ir4taControlAnual;
import com.indeci.rrhh.repository.CalculoSnapshotRepository;
import com.indeci.rrhh.repository.Ir4taControlAnualRepository;

/**
 * Tope anual de suspensión IR4ta — cubre los 10 casos de la especificación
 * (sección F): bandas de alerta, exceso estricto, topes GENERAL/DIRECTOR,
 * confirmación de reinicio y acumulado solo-INDECI.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Ir4taControlAnualServiceTest {

    @Mock private Ir4taControlAnualRepository controlRepo;
    @Mock private CalculoSnapshotRepository snapshotRepo;
    @Mock private Ir4taConfigService configService;
    @Mock private Suspension4taService suspension4taService;
    @Mock private com.indeci.audit.context.AuditoriaContext auditoriaContext;

    @InjectMocks private Ir4taControlAnualService service;

    private static final Long EMP = 41L;
    private static final int ANIO = 2026;

    private Ir4taConfigAnual cfg;

    @BeforeEach
    void setUp() {
        cfg = new Ir4taConfigAnual();
        cfg.setAnioFiscal(ANIO);
        cfg.setTopeAnualGeneral(new BigDecimal("48125.00"));
        cfg.setTopeAnualDirector(new BigDecimal("38500.00"));
        cfg.setPctAlertaPrev(new BigDecimal("80"));
        cfg.setPctAlertaCrit(new BigDecimal("90"));
        cfg.setFlgCalcAcumulado(1);
        cfg.setFlgMarcarValidacion(1);
        cfg.setFlgRetencionAuto(0);
        when(configService.resolverPorAnio(eq(ANIO), any(LocalDate.class)))
                .thenReturn(Optional.of(cfg));
    }

    // ── Bandas de estado (casos 1–5, 8) — calcularEstado directo ────────────

    @Test
    void caso1_vigente_bajo_80() {
        String e = service.calcularEstado(
                new BigDecimal("10000"), new BigDecimal("14500"),
                cfg.getTopeAnualGeneral(), cfg);
        assertThat(e).isEqualTo(Ir4taControlAnualService.EST_VIGENTE);
    }

    @Test
    void caso2_alerta_80() {
        // 40000/48125 = 83.1% → ALERTA_80, proyectado < tope.
        String e = service.calcularEstado(
                new BigDecimal("40000"), new BigDecimal("44000"),
                cfg.getTopeAnualGeneral(), cfg);
        assertThat(e).isEqualTo(Ir4taControlAnualService.EST_ALERTA_80);
    }

    @Test
    void caso3_alerta_90() {
        // 44000/48125 = 91.4% → ALERTA_90, proyectado < tope.
        String e = service.calcularEstado(
                new BigDecimal("44000"), new BigDecimal("46000"),
                cfg.getTopeAnualGeneral(), cfg);
        assertThat(e).isEqualTo(Ir4taControlAnualService.EST_ALERTA_90);
    }

    @Test
    void caso4_acumulado_igual_tope_no_exceso() {
        // proyectado == tope → NO excede (comparación estricta) → CERCA_DEL_TOPE.
        String e = service.calcularEstado(
                new BigDecimal("48125.00"), new BigDecimal("48125.00"),
                cfg.getTopeAnualGeneral(), cfg);
        assertThat(e).isEqualTo(Ir4taControlAnualService.EST_CERCA);
    }

    @Test
    void caso5_excede_tope_requiere_validacion() {
        // proyectado > tope → EXCEDE.
        String e = service.calcularEstado(
                new BigDecimal("45900"), new BigDecimal("50400"),
                cfg.getTopeAnualGeneral(), cfg);
        assertThat(e).isEqualTo(Ir4taControlAnualService.EST_EXCEDE);
    }

    // ── evaluarEnMotor (casos 6, 7, 9, 10) ──────────────────────────────────

    @Test
    void caso7_sin_suspension_no_cambia_motor() {
        var dec = service.evaluarEnMotor(EMP, ANIO, "2026-05",
                new BigDecimal("4500"), false, null);
        assertThat(dec.aplicarRetencionPeseASuspension()).isFalse();
        assertThat(dec.estadoControl()).isNull();
    }

    @Test
    void caso5b_excede_no_retiene_automatico_y_marca_validacion() {
        when(controlRepo.findByEmpleadoIdAndAnioFiscal(EMP, ANIO)).thenReturn(Optional.empty());
        when(snapshotRepo.sumarBaseIr4taPorAnio(eq(EMP), eq("2026%"), eq("2026-11")))
                .thenReturn(new BigDecimal("45900"));

        var dec = service.evaluarEnMotor(EMP, ANIO, "2026-11",
                new BigDecimal("4500"), true, null);

        // No retiene automáticamente (flgRetencionAuto = 0).
        assertThat(dec.aplicarRetencionPeseASuspension()).isFalse();
        ArgumentCaptor<Ir4taControlAnual> cap = ArgumentCaptor.forClass(Ir4taControlAnual.class);
        verify(controlRepo).save(cap.capture());
        Ir4taControlAnual saved = cap.getValue();
        assertThat(saved.getEstadoControl()).isEqualTo(Ir4taControlAnualService.EST_EXCEDE);
        assertThat(saved.getPeriodoExceso()).isEqualTo("2026-11");
        assertThat(saved.getFechaDeteccionExceso()).isNotNull();
        assertThat(saved.getAcumuladoIndeci()).isEqualByComparingTo("45900");
    }

    @Test
    void caso6_reinicio_confirmado_aplica_retencion() {
        Ir4taControlAnual ctrl = new Ir4taControlAnual();
        ctrl.setEmpleadoId(EMP);
        ctrl.setAnioFiscal(ANIO);
        ctrl.setTipoTope(Ir4taControlAnualService.TIPO_GENERAL);
        ctrl.setEstadoControl(Ir4taControlAnualService.EST_RET_ACTIVA);
        ctrl.setPeriodoReinicio("2026-12");
        when(controlRepo.findByEmpleadoIdAndAnioFiscal(EMP, ANIO)).thenReturn(Optional.of(ctrl));
        when(snapshotRepo.sumarBaseIr4taPorAnio(eq(EMP), eq("2026%"), eq("2026-12")))
                .thenReturn(new BigDecimal("50000"));

        var dec = service.evaluarEnMotor(EMP, ANIO, "2026-12",
                new BigDecimal("4500"), true, null);

        // Reinicio confirmado y período alcanzado → retiene pese a la constancia.
        assertThat(dec.aplicarRetencionPeseASuspension()).isTrue();
        assertThat(dec.estadoControl()).isEqualTo(Ir4taControlAnualService.EST_RET_ACTIVA);
    }

    @Test
    void caso9_tope_director_similar() {
        Ir4taControlAnual ctrl = new Ir4taControlAnual();
        ctrl.setEmpleadoId(EMP);
        ctrl.setAnioFiscal(ANIO);
        ctrl.setTipoTope(Ir4taControlAnualService.TIPO_DIRECTOR);
        ctrl.setEstadoControl(Ir4taControlAnualService.EST_VIGENTE);
        when(controlRepo.findByEmpleadoIdAndAnioFiscal(EMP, ANIO)).thenReturn(Optional.of(ctrl));
        // 39000 > 38500 (tope director) → EXCEDE; con tope general (48125) sería ALERTA_80.
        when(snapshotRepo.sumarBaseIr4taPorAnio(eq(EMP), eq("2026%"), eq("2026-10")))
                .thenReturn(new BigDecimal("39000"));

        var dec = service.evaluarEnMotor(EMP, ANIO, "2026-10",
                BigDecimal.ZERO, true, null);

        assertThat(dec.estadoControl()).isEqualTo(Ir4taControlAnualService.EST_EXCEDE);
        ArgumentCaptor<Ir4taControlAnual> cap = ArgumentCaptor.forClass(Ir4taControlAnual.class);
        verify(controlRepo).save(cap.capture());
        assertThat(cap.getValue().getTopeAnualAplicado()).isEqualByComparingTo("38500.00");
    }

    @Test
    void caso10_acumulado_solo_indeci_desde_snapshots() {
        // El acumulado proviene EXCLUSIVAMENTE de los snapshots IR4TA_CAS del año.
        when(controlRepo.findByEmpleadoIdAndAnioFiscal(EMP, ANIO)).thenReturn(Optional.empty());
        when(snapshotRepo.sumarBaseIr4taPorAnio(eq(EMP), eq("2026%"), eq("2026-03")))
                .thenReturn(new BigDecimal("12000"));

        service.evaluarEnMotor(EMP, ANIO, "2026-03", new BigDecimal("4000"), true, null);

        ArgumentCaptor<Ir4taControlAnual> cap = ArgumentCaptor.forClass(Ir4taControlAnual.class);
        verify(controlRepo).save(cap.capture());
        assertThat(cap.getValue().getAcumuladoIndeci()).isEqualByComparingTo("12000");
        // 12000/48125 = 24.9% → VIGENTE.
        assertThat(cap.getValue().getEstadoControl()).isEqualTo(Ir4taControlAnualService.EST_VIGENTE);
    }

    @Test
    void evaluarEnMotor_defensivo_no_propaga_error() {
        when(controlRepo.findByEmpleadoIdAndAnioFiscal(EMP, ANIO))
                .thenThrow(new RuntimeException("BD caída"));
        var dec = service.evaluarEnMotor(EMP, ANIO, "2026-05",
                new BigDecimal("4500"), true, null);
        assertThat(dec.aplicarRetencionPeseASuspension()).isFalse();
    }

    // ── confirmarReinicio (caso 6 — flujo RR.HH.) ───────────────────────────

    @Test
    void confirmarReinicio_exige_estado_excede() {
        Ir4taControlAnual ctrl = new Ir4taControlAnual();
        ctrl.setEstadoControl(Ir4taControlAnualService.EST_VIGENTE);
        when(controlRepo.findByEmpleadoIdAndAnioFiscal(EMP, ANIO)).thenReturn(Optional.of(ctrl));

        Ir4taReinicioInputDto in = new Ir4taReinicioInputDto();
        in.setAnioFiscal(ANIO);
        in.setPeriodoReinicio("2026-12");
        in.setSustento("Oficio 123");

        assertThatThrownBy(() -> service.confirmarReinicio(EMP, in, "rrhh"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("EXCEDE_TOPE_REQUIERE_VALIDACION");
    }

    @Test
    void confirmarReinicio_marca_retencion_activa() {
        Ir4taControlAnual ctrl = new Ir4taControlAnual();
        ctrl.setEmpleadoId(EMP);
        ctrl.setAnioFiscal(ANIO);
        ctrl.setTipoTope(Ir4taControlAnualService.TIPO_GENERAL);
        ctrl.setEstadoControl(Ir4taControlAnualService.EST_EXCEDE);
        when(controlRepo.findByEmpleadoIdAndAnioFiscal(EMP, ANIO)).thenReturn(Optional.of(ctrl));
        // obtenerControl (al final) necesita estas lecturas:
        when(suspension4taService.consultarVigente(eq(EMP), any(LocalDate.class)))
                .thenReturn(new Suspension4taVigenteDto(true, false, "C-1", null, null));
        when(snapshotRepo.sumarBaseIr4taPorAnio(eq(EMP), eq("2026%"), isNull()))
                .thenReturn(new BigDecimal("50000"));

        Ir4taReinicioInputDto in = new Ir4taReinicioInputDto();
        in.setAnioFiscal(ANIO);
        in.setPeriodoReinicio("2026-12");
        in.setSustento("Oficio 123 — supera tope");

        var dto = service.confirmarReinicio(EMP, in, "rrhh");

        ArgumentCaptor<Ir4taControlAnual> cap = ArgumentCaptor.forClass(Ir4taControlAnual.class);
        verify(controlRepo).save(cap.capture());
        assertThat(cap.getValue().getEstadoControl())
                .isEqualTo(Ir4taControlAnualService.EST_RET_ACTIVA);
        assertThat(cap.getValue().getPeriodoReinicio()).isEqualTo("2026-12");
        assertThat(cap.getValue().getConfirmadoPor()).isEqualTo("rrhh");
        assertThat(dto.getPeriodoReinicio()).isEqualTo("2026-12");
    }
}
