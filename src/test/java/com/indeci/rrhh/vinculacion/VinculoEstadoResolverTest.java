package com.indeci.rrhh.vinculacion;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.indeci.rrhh.vinculacion.VinculoEstadoResolver.VinculoEstado;

import static org.assertj.core.api.Assertions.assertThat;

/** Derivación del estado del vínculo (decisión RR.HH. 2026-07-02). */
class VinculoEstadoResolverTest {

    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);

    @Test
    void anulado_cuando_activo_cero() {
        assertThat(VinculoEstadoResolver.derivar(0, LocalDate.of(2026, 1, 1), null, null, HOY))
                .isEqualTo(VinculoEstado.ANULADO);
    }

    @Test
    void cesado_cuando_hay_fecha_de_cese() {
        assertThat(VinculoEstadoResolver.derivar(
                1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 6, 10), HOY))
                .isEqualTo(VinculoEstado.CESADO);
    }

    @Test
    void programado_cuando_inicio_es_futuro() {
        assertThat(VinculoEstadoResolver.derivar(1, LocalDate.of(2026, 7, 1), null, null, HOY))
                .isEqualTo(VinculoEstado.PROGRAMADO);
    }

    @Test
    void vencido_pendiente_cuando_fin_pasado_sin_cese() {
        assertThat(VinculoEstadoResolver.derivar(
                1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31), null, HOY))
                .isEqualTo(VinculoEstado.VENCIDO_PENDIENTE_DE_REGULARIZACION);
    }

    @Test
    void vigente_en_curso() {
        assertThat(VinculoEstadoResolver.derivar(
                1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, HOY))
                .isEqualTo(VinculoEstado.VIGENTE);
        // Sin fecha fin también es vigente si ya inició.
        assertThat(VinculoEstadoResolver.derivar(1, LocalDate.of(2026, 1, 1), null, null, HOY))
                .isEqualTo(VinculoEstado.VIGENTE);
    }

    @Test
    void lbs_solo_con_cese_formal_completo() {
        assertThat(VinculoEstadoResolver.habilitaLbs(
                VinculoEstado.CESADO, LocalDate.of(2026, 6, 10), "Vencimiento de contrato", "RES-123"))
                .isTrue();
        // Falta documento → no habilita.
        assertThat(VinculoEstadoResolver.habilitaLbs(
                VinculoEstado.CESADO, LocalDate.of(2026, 6, 10), "Vencimiento", null))
                .isFalse();
        // Vencido pendiente (sin cese formal) → no habilita.
        assertThat(VinculoEstadoResolver.habilitaLbs(
                VinculoEstado.VENCIDO_PENDIENTE_DE_REGULARIZACION, null, null, null))
                .isFalse();
    }
}
