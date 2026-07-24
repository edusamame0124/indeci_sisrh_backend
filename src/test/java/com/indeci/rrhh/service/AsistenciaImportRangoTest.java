package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaImportConfirmRequest;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.PeriodoPlanilla;

/**
 * Fase 1 — rango de días CUBIERTO por la carga de asistencia. Verifica la resolución
 * (declarado &gt; detectado &gt; mes completo) y la validación (coherencia + dentro del mes)
 * que acotan la generación de FALTAS por calendario. Unit test puro (sin Spring/H2).
 */
class AsistenciaImportRangoTest {

    private static final LocalDate MES_INI = LocalDate.of(2026, 7, 1);
    private static final LocalDate MES_FIN = LocalDate.of(2026, 7, 31);

    private PeriodoPlanilla periodo() {
        PeriodoPlanilla p = new PeriodoPlanilla();
        p.setFechaInicio(MES_INI);
        p.setFechaFin(MES_FIN);
        return p;
    }

    private AsistenciaImportacion importacion(LocalDate detIni, LocalDate detFin) {
        AsistenciaImportacion imp = new AsistenciaImportacion();
        imp.setPeriodoDetectadoIni(detIni);
        imp.setPeriodoDetectadoFin(detFin);
        return imp;
    }

    private AsistenciaImportConfirmRequest request(LocalDate ini, LocalDate fin) {
        AsistenciaImportConfirmRequest r = new AsistenciaImportConfirmRequest();
        r.setDiaInicio(ini);
        r.setDiaFin(fin);
        return r;
    }

    @Test
    void resolver_prioriza_lo_declarado_en_el_request() {
        AsistenciaImportConfirmRequest req = request(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 10));
        AsistenciaImportacion imp = importacion(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15));

        assertThat(AsistenciaImportService.resolverRangoIni(req, imp, periodo()))
                .isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(AsistenciaImportService.resolverRangoFin(req, imp, periodo()))
                .isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    void resolver_cae_al_detectado_si_no_hay_declarado() {
        AsistenciaImportConfirmRequest req = request(null, null);
        AsistenciaImportacion imp = importacion(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15));

        assertThat(AsistenciaImportService.resolverRangoIni(req, imp, periodo()))
                .isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(AsistenciaImportService.resolverRangoFin(req, imp, periodo()))
                .isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void resolver_cae_al_mes_completo_si_no_hay_declarado_ni_detectado() {
        AsistenciaImportConfirmRequest req = request(null, null);
        AsistenciaImportacion imp = importacion(null, null);

        assertThat(AsistenciaImportService.resolverRangoIni(req, imp, periodo())).isEqualTo(MES_INI);
        assertThat(AsistenciaImportService.resolverRangoFin(req, imp, periodo())).isEqualTo(MES_FIN);
    }

    @Test
    void validar_ok_dentro_del_mes() {
        assertThatCode(() -> AsistenciaImportService.validarRangoCubierto(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15), periodo()))
                .doesNotThrowAnyException();
    }

    @Test
    void validar_falla_si_fin_antes_que_inicio() {
        assertThatThrownBy(() -> AsistenciaImportService.validarRangoCubierto(
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 1), periodo()))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    void validar_falla_si_rango_sale_del_mes() {
        assertThatThrownBy(() -> AsistenciaImportService.validarRangoCubierto(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 5), periodo()))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("dentro del periodo");
    }

    @Test
    void validar_no_falla_con_rango_nulo() {
        assertThatCode(() -> AsistenciaImportService.validarRangoCubierto(null, null, periodo()))
                .doesNotThrowAnyException();
    }
}
