package com.indeci.rrhh.dto;

import java.time.LocalDate;

/**
 * Vista enriquecida del tiempo de servicio para Configuración Remunerativa — SPEC_VACACIONES F9.1
 * + V012_42 F1 (tiempo efectivo). Combina la antigüedad de vínculo bruta (F1, {@code tiempoServicio})
 * con los días NO computables (LSG + faltas + suspensiones) y el tiempo de servicio EFECTIVO
 * (bruto − no computables, re-desglosado en años/meses/días base 30/360), para que RR.HH. entienda
 * por qué el récord puede diferir de la antigüedad bruta. Read-only.
 *
 * <p><b>Importante:</b> {@code tiempoServicio} (F1, {@link TiempoServicioDto}) NUNCA se modifica —
 * lo consumen Padrón Vacacional / LBS / Acumulación con su propia resta de incidencias downstream;
 * volverlo neto aquí causaría doble descuento en esos módulos. El tiempo efectivo vive
 * EXCLUSIVAMENTE en este DTO, aditivo, para Configuración Remunerativa.</p>
 *
 * @param tiempoServicio      antigüedad de vínculo bruta 30/360 (base CTS/LBS/Padrón/Acumulación);
 *                            {@code null} si sin vínculo
 * @param diasNoComputables   desglose LSG + faltas + suspensiones sobre todo el tiempo de servicio
 * @param aniversarioEfectivo estimación del próximo aniversario neto de incidencias; {@code null} si sin vínculo
 * @param aniosEfectivos      años del tiempo de servicio EFECTIVO (bruto − no computables, 30/360)
 * @param mesesEfectivos      meses del tiempo de servicio EFECTIVO (0-11)
 * @param diasEfectivos       días del tiempo de servicio EFECTIVO (0-29)
 * @param totalDiasEfectivos  total en días comerciales del tiempo de servicio EFECTIVO
 */
public record TiempoServicioDetalleDto(
        TiempoServicioDto tiempoServicio,
        DiasNoComputablesDto diasNoComputables,
        LocalDate aniversarioEfectivo,
        int aniosEfectivos,
        int mesesEfectivos,
        int diasEfectivos,
        int totalDiasEfectivos) {

    public static TiempoServicioDetalleDto sinVinculo() {
        return new TiempoServicioDetalleDto(null, DiasNoComputablesDto.cero(), null, 0, 0, 0, 0);
    }
}
