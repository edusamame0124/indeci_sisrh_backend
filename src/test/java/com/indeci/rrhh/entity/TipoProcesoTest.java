package com.indeci.rrhh.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Track B F1 — mapeo legacy → TipoProceso y derivación en PlanillaLote. */
class TipoProcesoTest {

    @Test
    void mapea_tipo_planilla_legacy_a_tipo_proceso() {
        assertThat(TipoProceso.fromTipoPlanilla(null)).isEqualTo(TipoProceso.REGULAR);
        assertThat(TipoProceso.fromTipoPlanilla("")).isEqualTo(TipoProceso.REGULAR);
        assertThat(TipoProceso.fromTipoPlanilla("ORDINARIA")).isEqualTo(TipoProceso.REGULAR);
        assertThat(TipoProceso.fromTipoPlanilla("GENERADO")).isEqualTo(TipoProceso.REGULAR);
        assertThat(TipoProceso.fromTipoPlanilla("ADICIONAL")).isEqualTo(TipoProceso.ADICIONAL);
        assertThat(TipoProceso.fromTipoPlanilla("ADICIONAL_2")).isEqualTo(TipoProceso.ADICIONAL);
        assertThat(TipoProceso.fromTipoPlanilla("reintegro")).isEqualTo(TipoProceso.REINTEGRO);
        assertThat(TipoProceso.fromTipoPlanilla("AGUINALDO")).isEqualTo(TipoProceso.AGUINALDO);
        assertThat(TipoProceso.fromTipoPlanilla("AGUINALDO_2026-07")).isEqualTo(TipoProceso.AGUINALDO);
        assertThat(TipoProceso.fromTipoPlanilla("LBS")).isEqualTo(TipoProceso.LBS);
    }

    @Test
    void planilla_lote_deriva_su_tipo_proceso() {
        PlanillaLote lote = new PlanillaLote();
        lote.setTipoPlanilla("ADICIONAL");
        assertThat(lote.getTipoProceso()).isEqualTo(TipoProceso.ADICIONAL);

        lote.setTipoPlanilla("ORDINARIA");
        assertThat(lote.getTipoProceso()).isEqualTo(TipoProceso.REGULAR);
    }
}
