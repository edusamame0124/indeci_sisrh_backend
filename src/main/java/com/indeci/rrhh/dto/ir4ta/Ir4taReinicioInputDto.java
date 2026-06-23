package com.indeci.rrhh.dto.ir4ta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Entrada para confirmar el reinicio de retención IR4ta tras superar el tope
 * anual (Wireframe B — "Confirmación de reinicio de retención"). Solo rol
 * autorizado (PLA_APPROVE). Exige período de inicio y sustento documentado.
 */
@Data
public class Ir4taReinicioInputDto {

    @NotNull(message = "El año fiscal es obligatorio.")
    private Integer anioFiscal;

    @NotBlank(message = "El período de reinicio es obligatorio.")
    @Pattern(regexp = "\\d{4}-?\\d{2}", message = "Período inválido. Use YYYY-MM o YYYYMM.")
    private String periodoReinicio;

    @NotBlank(message = "El sustento es obligatorio.")
    @Size(max = 1000)
    private String sustento;

    @Size(max = 500)
    private String observacion;
}
