package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.indeci.rrhh.dto.VacacionCalculoDto;
import com.indeci.rrhh.dto.VacacionCalculoInput;

/**
 * SPEC_VACACIONES F3 — VacacionCalculoService.
 * Regresión 1:1 contra la hoja DATOS de la MATRIZ_VACACIONES (cols P/R/T/U/V/W).
 */
class VacacionCalculoServiceTest {

    private final VacacionCalculoService service = new VacacionCalculoService((empId, desde, hasta) -> 0);

    // ---------- Regresión Excel: AGUIRRE HUAMANI (fila 9) ----------
    @Test
    void regresion_excel_aguirre() {
        // S=2364.19, L/M/N=7/5/17, gozados=180, jornada 6d.
        VacacionCalculoInput in = new VacacionCalculoInput(
                null, null, null, 7, 5, 17, 2687, 210d, 180d, new BigDecimal("2364.19"), 6, 0);

        VacacionCalculoDto r = service.calcular(in);

        assertThat(r.diasCorresponden()).isEqualTo(210);          // P
        assertThat(r.saldo()).isEqualTo(30d);                     // R
        assertThat(r.costoNoGozadas()).isEqualByComparingTo("2364.19"); // T
        assertThat(r.costoTruncasMes()).isEqualByComparingTo("985.08"); // U
        assertThat(r.costoTruncasDia()).isEqualByComparingTo("111.64"); // V
        assertThat(r.costoTotal()).isEqualByComparingTo("3460.91");     // W
        assertThat(r.estadoRecord()).isEqualTo(VacacionCalculoDto.RECORD_OK);
        assertThat(r.umbralRecord()).isEqualTo(260);
    }

    // ---------- Regresión Excel: AGAPITO (fila 6) — saldo NEGATIVO ----------
    @Test
    void regresion_excel_agapito_saldo_negativo() {
        // S=6800, L/M/N=1/8/25, gozados=33 (gozó de más), jornada 6d.
        VacacionCalculoInput in = new VacacionCalculoInput(
                null, null, null, 1, 8, 25, 691, 30d, 33d, new BigDecimal("6800"), 6, 0);

        VacacionCalculoDto r = service.calcular(in);

        assertThat(r.diasCorresponden()).isEqualTo(30);           // P
        assertThat(r.saldo()).isEqualTo(-3d);                     // R negativo
        assertThat(r.costoNoGozadas()).isEqualByComparingTo("-680.00"); // T negativo
        assertThat(r.costoTruncasMes()).isEqualByComparingTo("4533.33"); // U
        assertThat(r.costoTruncasDia()).isEqualByComparingTo("472.22");  // V
        assertThat(r.costoTotal()).isEqualByComparingTo("4325.55");      // W
    }

    // ---------- Récord D5: SIN_RECORD_LEGAL cuando días efectivos < umbral ----------
    @Test
    void record_sin_cuando_efectivos_bajo_umbral() {
        // 1 año cronológico (~361 días) pero 200 días no computables → 161 efectivos < 260.
        VacacionCalculoInput in = new VacacionCalculoInput(
                null, null, null, 1, 0, 0, 361, 30d, 0d, new BigDecimal("3000"), 6, 200);

        VacacionCalculoDto r = service.calcular(in);

        assertThat(r.diasEfectivos()).isEqualTo(161);
        assertThat(r.umbralRecord()).isEqualTo(260);
        assertThat(r.estadoRecord()).isEqualTo(VacacionCalculoDto.RECORD_SIN);
    }

    // ---------- Récord: umbral 210 en jornada de 5 días ----------
    @Test
    void record_ok_jornada_5_umbral_210() {
        // 220 días efectivos, jornada 5d (umbral 210) → OK.
        VacacionCalculoInput in = new VacacionCalculoInput(
                null, null, null, 1, 0, 0, 220, 30d, 0d, new BigDecimal("3000"), 5, 0);

        VacacionCalculoDto r = service.calcular(in);

        assertThat(r.umbralRecord()).isEqualTo(210);
        assertThat(r.estadoRecord()).isEqualTo(VacacionCalculoDto.RECORD_OK);
    }

    // ---------- Borde: sin años completos → corresponden 0, saldo negativo por gozados ----------
    @Test
    void sin_anios_completos_corresponden_cero() {
        VacacionCalculoInput in = new VacacionCalculoInput(
                null, null, null, 0, 2, 0, 61, 0d, 0d, new BigDecimal("4364.19"), 6, 0);

        VacacionCalculoDto r = service.calcular(in);

        assertThat(r.diasCorresponden()).isZero();
        assertThat(r.saldo()).isZero();
        assertThat(r.costoNoGozadas()).isEqualByComparingTo("0.00");
    }
}
