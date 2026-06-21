package com.indeci.rrhh.dto.ir4ta;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTO para duplicar una vigencia IR4ta en un nuevo año fiscal. */
@Data
public class Ir4taConfigDuplicarInputDto {

    @NotNull(message = "El año fiscal de destino es obligatorio.")
    @Min(value = 2000) @Max(value = 2100)
    private Integer anioFiscal;

    @NotNull(message = "La fecha de inicio de vigencia es obligatoria.")
    private LocalDate vigenciaInicio;

    private LocalDate vigenciaFin;

    @NotNull(message = "La UIT vigente del nuevo período es obligatoria.")
    @DecimalMin("0.01")
    private BigDecimal uitVigente;

    @NotBlank(message = "La fuente normativa es obligatoria.")
    @Size(max = 500)
    private String fuenteOficial;

    @Size(max = 200)
    private String observacion;
}
