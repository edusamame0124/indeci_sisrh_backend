package com.indeci.rrhh.dto.essalud;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EssaludDuplicarInputDto {

    @NotNull(message = "La fecha de inicio de la nueva vigencia es obligatoria.")
    private LocalDate vigenciaInicio;

    private LocalDate vigenciaFin;

    @NotNull(message = "La UIT vigente es obligatoria.")
    @DecimalMin(value = "0.01", message = "La UIT debe ser mayor a cero.")
    private BigDecimal uitVigente;

    @NotBlank(message = "La fuente oficial es obligatoria.")
    private String fuenteOficial;

    private String observacion;
}
