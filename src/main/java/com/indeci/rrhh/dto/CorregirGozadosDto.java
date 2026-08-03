package com.indeci.rrhh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * Botón "Editar Gozados" del Padrón Vacacional — corrección manual del total de días
 * gozados de un empleado. El motivo es obligatorio (Poka-Yoke) y queda en AUDITORIA y en
 * un registro nuevo de {@code INDECI_VACACIONES} (trazabilidad, ver {@code VacacionService
 * #corregirGozadoManual}).
 */
@Data
public class CorregirGozadosDto {

    @NotNull(message = "El nuevo total de días gozados es obligatorio")
    @PositiveOrZero(message = "El total de días gozados no puede ser negativo")
    private Double nuevoTotalGozado;

    @NotBlank(message = "El motivo de la corrección es obligatorio")
    private String motivo;
}
