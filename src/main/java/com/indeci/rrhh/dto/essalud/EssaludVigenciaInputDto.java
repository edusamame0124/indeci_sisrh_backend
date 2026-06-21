package com.indeci.rrhh.dto.essalud;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EssaludVigenciaInputDto {

    @NotNull(message = "La fecha de inicio es obligatoria.")
    private LocalDate vigenciaInicio;

    private LocalDate vigenciaFin;

    @NotNull(message = "La UIT vigente es obligatoria.")
    @DecimalMin(value = "0.01", message = "La UIT debe ser mayor a cero.")
    private BigDecimal uitVigente;

    @NotNull @DecimalMin("0") @DecimalMax("100")
    private BigDecimal pctBaseCas;

    @NotNull @DecimalMin("0") @DecimalMax("100")
    private BigDecimal pctEssalud;

    @NotNull @DecimalMin("0") @DecimalMax("100")
    private BigDecimal pctEssaludEps;

    @NotNull @DecimalMin("0") @DecimalMax("100")
    private BigDecimal pctCreditoEps;

    @NotBlank(message = "La fuente oficial es obligatoria.")
    private String fuenteOficial;

    private String urlFuenteOficial;
    private LocalDate fechaPublicacion;
    private String observacion;
}
