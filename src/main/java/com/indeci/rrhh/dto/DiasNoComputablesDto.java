package com.indeci.rrhh.dto;

/**
 * Desglose de días NO computables al récord vacacional / tiempo de servicio en un período —
 * SPEC_VACACIONES F9.1 (D.Leg. 1405 / D.S. 013-2019-PCM art. 11) + V012_42 F1 (histórico migrado).
 * Se muestran separados para trazabilidad de RR.HH.
 *
 * @param lsg          días de Licencia Sin Goce (suspensión perfecta) del período, operativa
 *                     (papeleta) o histórica migrada bajo el mismo código {@code LICENCIA_SIN_GOCE}
 * @param faltas       días de inasistencia injustificada: operativa ({@code TIPO_DIA='FALTA'}) +
 *                     histórica migrada ({@code FALTA_HISTORICA})
 * @param suspensiones días de suspensión/sanción PAD histórica migrada ({@code SUSPENSION_HISTORICA})
 * @param total        lsg + faltas + suspensiones
 */
public record DiasNoComputablesDto(int lsg, int faltas, int suspensiones, int total) {

    /** Compatibilidad con llamadores previos a V012_42 (sin histórico de suspensión). */
    public static DiasNoComputablesDto of(int lsg, int faltas) {
        return of(lsg, faltas, 0);
    }

    public static DiasNoComputablesDto of(int lsg, int faltas, int suspensiones) {
        return new DiasNoComputablesDto(lsg, faltas, suspensiones, lsg + faltas + suspensiones);
    }

    public static DiasNoComputablesDto cero() {
        return new DiasNoComputablesDto(0, 0, 0, 0);
    }
}
