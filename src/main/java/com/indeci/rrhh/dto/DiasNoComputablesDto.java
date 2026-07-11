package com.indeci.rrhh.dto;

/**
 * Desglose de días NO computables al récord vacacional en un período — SPEC_VACACIONES F9.1
 * (D.Leg. 1405 / D.S. 013-2019-PCM art. 11). Se muestran separados para trazabilidad de RR.HH.
 *
 * @param lsg    días de Licencia Sin Goce (suspensión perfecta) del período
 * @param faltas días de inasistencia injustificada (TIPO_DIA='FALTA') del período
 * @param total  lsg + faltas
 */
public record DiasNoComputablesDto(int lsg, int faltas, int total) {

    public static DiasNoComputablesDto of(int lsg, int faltas) {
        return new DiasNoComputablesDto(lsg, faltas, lsg + faltas);
    }

    public static DiasNoComputablesDto cero() {
        return new DiasNoComputablesDto(0, 0, 0);
    }
}
