package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Track B — Cálculo del AGUINALDO por régimen (proceso APARTE, no en la planilla
 * regular). Reglas confirmadas por RR.HH. (2026-07-03):
 *
 * <ul>
 *   <li><b>SERVIR (30057)</b> → 100% del sueldo (base remunerativa mensual).
 *       <br>Base legal: <b>Ley N° 30057</b> (Ley del Servicio Civil) y su
 *       Reglamento de Compensaciones.</li>
 *   <li><b>CAS (1057)</b> → % manual × base, con <b>piso legal</b>
 *       {@code MAX(calculado, pisoCas)}. El % lo define RR.HH. según la normativa
 *       vigente del ejercicio: es <b>input manual por proceso</b> (viaja en el
 *       request), NUNCA se hardcodea ni se persiste como parámetro fijo. El piso
 *       S/ 300 sí es parámetro ({@code AGUINALDO_CAS_PISO}).
 *       <br>Referencia normativa del % <b>PENDIENTE_VALIDACION</b> hasta la
 *       publicación del D.S. del MEF del ejercicio.</li>
 *   <li><b>276</b> → monto fijo parametrizado ({@code monto276}, viene de BD).
 *       <br>Base legal: <b>Ley N° 32513</b> (Presupuesto del Sector Público 2026).</li>
 *   <li>Otro régimen (p. ej. 728) → 0 (no genera aguinaldo por esta vía).</li>
 * </ul>
 *
 * <p>Clase pura (sin Spring/BD) para ser testeable directamente. La {@code base},
 * el {@code pctCas} (request), el {@code monto276} y el {@code pisoCas} (parámetros)
 * los resuelve el llamador.</p>
 */
public final class AguinaldoCalculator {

    private AguinaldoCalculator() {}

    private static final BigDecimal CIEN = new BigDecimal("100");

    /**
     * @param regimenCodigo código del régimen ("SERVIR", "CAS"/"1057", "276", …).
     * @param base          remuneración mensual del trabajador (SERVIR y CAS).
     * @param pctCas        porcentaje manual de RR.HH. para CAS (en %, 100 = 100%);
     *                      input del proceso, no un parámetro persistido.
     * @param monto276      monto fijo parametrizado del aguinaldo 276.
     * @param pisoCas       piso legal del aguinaldo CAS (S/ 300). CAS = MAX(calc, pisoCas).
     * @return monto del aguinaldo (2 decimales, HALF_UP); 0 si el régimen no aplica.
     */
    public static BigDecimal calcular(
            String regimenCodigo, BigDecimal base, BigDecimal pctCas,
            BigDecimal monto276, BigDecimal pisoCas) {

        if (regimenCodigo == null) {
            return BigDecimal.ZERO;
        }
        String r = regimenCodigo.trim().toUpperCase();

        if ("SERVIR".equals(r) || "30057".equals(r)) {
            return escala(base); // 100% del sueldo
        }
        if ("CAS".equals(r) || "1057".equals(r)) {
            if (base == null || pctCas == null) {
                return BigDecimal.ZERO;
            }
            BigDecimal calculado = base.multiply(pctCas).divide(CIEN, 6, RoundingMode.HALF_UP);
            // Piso legal CAS (Ley 32563 + gradualidad MEF): nunca menor a AGUINALDO_CAS_PISO.
            BigDecimal piso = pisoCas != null ? pisoCas : BigDecimal.ZERO;
            return escala(calculado.max(piso));
        }
        if ("276".equals(r)) {
            return escala(monto276);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Bases tributarias que alimenta el aguinaldo (Track B pto 3):
     * afecto a <b>renta de 5ta</b> (suma a la proyección anual), NO afecto a
     * pensión (AFP/ONP) ni a EsSalud.
     */
    public record BasesAfectas(BigDecimal renta5ta, BigDecimal pension, BigDecimal essalud) {}

    /**
     * @param montoAguinaldo monto del aguinaldo del trabajador.
     * @return bases afectas: renta5ta = monto; pensión = 0; essalud = 0.
     */
    public static BasesAfectas basesAfectas(BigDecimal montoAguinaldo) {
        BigDecimal m = montoAguinaldo != null
                ? montoAguinaldo.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new BasesAfectas(m, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static BigDecimal escala(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }
}
