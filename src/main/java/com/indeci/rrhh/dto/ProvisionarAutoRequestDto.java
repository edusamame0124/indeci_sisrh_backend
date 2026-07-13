package com.indeci.rrhh.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * "Provisionar Auto" (Padrón Vacacional) — el sustento es obligatorio en TODA invocación
 * (Poka-Yoke: no hay camino silencioso). Se persiste íntegro en AUDITORIA (vía
 * {@code @Auditable}) y truncado como marcador corto en la columna OBSERVACION de las filas
 * afectadas.
 */
@Data
public class ProvisionarAutoRequestDto {

    @NotBlank(message = "El sustento de la provisión/recálculo es obligatorio")
    private String sustento;
}
