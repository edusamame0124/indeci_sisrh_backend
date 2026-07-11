package com.indeci.rrhh.dto;

import java.time.LocalDate;

/**
 * Hub Vacacional — período de vacaciones ya aprobado (materializado en {@code INDECI_VACACIONES})
 * y aún NO gozado (fecha de inicio futura), disponible para reprogramar o fraccionar.
 *
 * <p>Fuente del dropdown Poka-Yoke que reemplaza el ingreso manual de fechas en
 * {@code REPROG_ACTUAL}/{@code FRACC_ACTUAL}.</p>
 */
public record PeriodoProgramadoDto(
        Long id,
        LocalDate periodoDesde,
        LocalDate periodoHasta,
        Integer dias,
        String tipoGoce) {
}
