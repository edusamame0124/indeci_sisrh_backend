package com.indeci.rrhh.dto;

/** Resultado de {@code VacacionService#corregirGozadoManual} — antes/después del total gozado. */
public record CorreccionGozadosResultDto(
        double gozadoAnterior,
        double gozadoNuevo,
        double delta) {
}
