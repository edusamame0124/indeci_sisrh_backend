package com.indeci.rrhh.entity;

/**
 * Track B F1 — Tipo de proceso de planilla CAS. Formaliza el vocabulario del
 * flujo "Nuevo proceso de planilla" sobre el {@code tipoPlanilla} existente de
 * {@link PlanillaLote}, sin romper datos ni cálculo.
 *
 * <ul>
 *   <li>{@code REGULAR} — planilla ordinaria del período (equivale al legacy ORDINARIA).</li>
 *   <li>{@code ADICIONAL} — proceso adicional selectivo (correlativo 01/02/03).</li>
 *   <li>{@code REINTEGRO} — montos pendientes con origen identificado.</li>
 *   <li>{@code AGUINALDO} — aguinaldo jul/dic generado APARTE (SERVIR 100% / CAS %manual / 276 fijo).</li>
 *   <li>{@code LBS} — Liquidación de Beneficios Sociales (desde vínculo cesado).</li>
 * </ul>
 *
 * <p>{@code REINTEGRO}/{@code LBS} quedan definidos para las fases siguientes de
 * Track B; el mapeo desde el legacy produce {@code REGULAR}/{@code ADICIONAL}/
 * {@code AGUINALDO}, que son los tipos que hoy generan lote.</p>
 */
public enum TipoProceso {
    REGULAR,
    ADICIONAL,
    REINTEGRO,
    AGUINALDO,
    LBS;

    /** Deriva el tipo de proceso desde el {@code tipoPlanilla} legacy del lote. */
    public static TipoProceso fromTipoPlanilla(String tipoPlanilla) {
        if (tipoPlanilla == null || tipoPlanilla.isBlank()) {
            return REGULAR;
        }
        String v = tipoPlanilla.trim().toUpperCase();
        if (v.startsWith("ADICIONAL")) {
            return ADICIONAL;
        }
        if (v.startsWith("REINTEGRO")) {
            return REINTEGRO;
        }
        if (v.startsWith("AGUINALDO")) {
            return AGUINALDO;
        }
        if (v.equals("LBS")) {
            return LBS;
        }
        return REGULAR; // ORDINARIA, GENERADO, u otros legacy → proceso regular
    }
}
