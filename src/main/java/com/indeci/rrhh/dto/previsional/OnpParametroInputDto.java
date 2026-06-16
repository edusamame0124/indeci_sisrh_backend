package com.indeci.rrhh.dto.previsional;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OnpParametroInputDto {

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Formato YYYYMM requerido")
    private String periodoInicio;

    @Pattern(regexp = "\\d{6}", message = "Formato YYYYMM requerido")
    private String periodoFin;

    @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
    private BigDecimal aporteOnpPct;

    @NotBlank
    private String fuenteOficial;

    private String    urlFuenteOficial;
    private LocalDate fechaPublicacion;
    private String    observacion;
}
