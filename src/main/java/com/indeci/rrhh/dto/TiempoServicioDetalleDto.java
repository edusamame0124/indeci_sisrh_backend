package com.indeci.rrhh.dto;

import java.time.LocalDate;

/**
 * Vista enriquecida del tiempo de servicio para Configuración Remunerativa — SPEC_VACACIONES F9.1.
 * Combina la antigüedad de vínculo (F1) con los días NO computables (LSG + faltas) y una
 * estimación del aniversario vacacional efectivo, para que RR.HH. entienda por qué el récord
 * puede diferir de la antigüedad bruta. Read-only.
 *
 * @param tiempoServicio      antigüedad de vínculo 30/360 (base CTS/LBS/quinquenios); {@code null} si sin vínculo
 * @param diasNoComputables   desglose LSG + faltas sobre todo el tiempo de servicio
 * @param aniversarioEfectivo estimación del próximo aniversario neto de incidencias; {@code null} si sin vínculo
 */
public record TiempoServicioDetalleDto(
        TiempoServicioDto tiempoServicio,
        DiasNoComputablesDto diasNoComputables,
        LocalDate aniversarioEfectivo) {
}
