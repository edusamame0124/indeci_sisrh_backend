package com.indeci.rrhh.dto;

import java.time.LocalDate;

/**
 * Un período del récord vacacional (ventana aniversario a aniversario del ingreso) con sus
 * incidencias propias y si cumplió el récord — SPEC_VACACIONES F9.1 (récord anual estricto).
 * Es la unidad de la tabla "Detalle de récord vacacional" (Opción A): explica período por
 * período de dónde salen los "Días que corresponden".
 *
 * @param numero        n.º de período (1 = primer año de servicio, etc.)
 * @param desde         inicio de la ventana (aniversario − 1 año)
 * @param hasta         fin de la ventana (día previo al aniversario)
 * @param lsg           días de licencia sin goce del período (no computables)
 * @param faltas        días de falta injustificada del período (no computables)
 * @param suspensiones  días de suspensión/sanción del período (no computables)
 * @param diasEfectivos días efectivos del período (30/360, neto de incidencias)
 * @param recordOk      true si el período alcanzó el récord (genera 30 días)
 * @param diasGanados   30 si cumple récord, 0 si no
 */
public record PeriodoRecordDto(
        int numero,
        LocalDate desde,
        LocalDate hasta,
        int lsg,
        int faltas,
        int suspensiones,
        int diasEfectivos,
        boolean recordOk,
        int diasGanados) {
}
