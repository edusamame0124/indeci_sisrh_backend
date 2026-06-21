package com.indeci.rrhh.dto.previsional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Datos que el usuario debe confirmar al duplicar una vigencia previsional.
 * Obligatorio capturar fuente y motivo para garantizar trazabilidad normativa.
 * Base: TUO SPP D.S. 054-97-EF / Ley 19990.
 */
@Data
public class DuplicarVigenciaRequestDto {

    /** Período de inicio de la nueva vigencia. Debe ser posterior al origen. */
    @NotBlank(message = "El período de inicio es obligatorio.")
    @Pattern(regexp = "^[0-9]{4}(0[1-9]|1[0-2])$", message = "Formato YYYYMM requerido (mes 01–12).")
    private String periodoInicio;

    /** Circular, resolución o norma que ampara los nuevos valores. */
    @NotBlank(message = "La fuente oficial es obligatoria para trazabilidad normativa.")
    private String fuenteOficial;

    /**
     * Motivo explícito de la duplicación.
     * Requerido para mantener trazabilidad de cambios regulatorios (SBS, ONP).
     */
    @NotBlank(message = "Indique el motivo de la duplicación para mantener trazabilidad.")
    private String observacion;
}
