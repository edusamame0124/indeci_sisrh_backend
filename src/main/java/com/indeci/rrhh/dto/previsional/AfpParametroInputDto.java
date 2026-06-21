package com.indeci.rrhh.dto.previsional;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AfpParametroInputDto {

    @NotNull
    private Long afpId;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Formato YYYYMM requerido")
    private String periodoInicio;

    @Pattern(regexp = "\\d{6}", message = "Formato YYYYMM requerido")
    private String periodoFin;

    @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
    private BigDecimal aporteObligatorioPct;

    @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
    private BigDecimal comisionFlujoPct;

    @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
    private BigDecimal comisionSaldoAnualPct;

    @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
    private BigDecimal primaSeguroPct;

    @NotNull @DecimalMin("0.0")
    private BigDecimal remuneracionMaximaAsegurable;

    @NotBlank
    private String fuenteOficial;

    private String   urlFuenteOficial;
    private LocalDate fechaPublicacion;
    private String   observacion;
}
