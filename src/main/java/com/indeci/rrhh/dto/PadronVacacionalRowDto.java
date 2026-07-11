package com.indeci.rrhh.dto;

/**
 * Fila del padrón vacacional — SPEC_VACACIONES F4/F5/F9. Columnas del Excel del especialista
 * (DNI, nombre, régimen, cargo, dependencia, días que corresponden, gozados, saldo) +
 * tiempo de servicio, <b>días no computables (LSG + faltas)</b>, tiempo efectivo y récord.
 *
 * @param diasNoComputablesLsg    días de Licencia Sin Goce (no computan al récord — art. 11 D.S. 013-2019-PCM)
 * @param diasNoComputablesFaltas días de falta injustificada (no computan al récord)
 * @param aniosEfectivos          años efectivos = (tiempo de servicio − no computables) 30/360
 * @param mesesEfectivos          meses efectivos (0-11)
 * @param diasEfectivos           días efectivos (0-29)
 * @param estadoRecord            evaluado sobre los días EFECTIVOS (netos de LSG/faltas) vs umbral por jornada
 */
public record PadronVacacionalRowDto(
        Long empleadoId,
        String dni,
        String nombreCompleto,
        String regimenLaboral,
        String cargo,
        String dependencia,
        Integer aniosServicio,
        Integer mesesServicio,
        Integer diasServicio,
        Integer diasNoComputablesLsg,
        Integer diasNoComputablesFaltas,
        Integer aniosEfectivos,
        Integer mesesEfectivos,
        Integer diasEfectivos,
        int diasCorresponden,
        double diasGozados,
        double saldo,
        String estadoRecord,
        boolean sinVinculo) {
}
