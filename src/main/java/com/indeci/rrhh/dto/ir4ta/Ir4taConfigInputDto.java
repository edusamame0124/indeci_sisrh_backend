package com.indeci.rrhh.dto.ir4ta;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTO de entrada para crear o editar configuración anual IR4ta. */
@Data
public class Ir4taConfigInputDto {

    @NotNull(message = "El año fiscal es obligatorio.")
    @Min(value = 2000, message = "Año fiscal inválido.")
    @Max(value = 2100, message = "Año fiscal inválido.")
    private Integer anioFiscal;

    @NotNull(message = "La fecha de inicio de vigencia es obligatoria.")
    private LocalDate vigenciaInicio;

    private LocalDate vigenciaFin;

    @NotNull(message = "La UIT vigente es obligatoria.")
    @DecimalMin(value = "0.01", message = "La UIT debe ser mayor a cero.")
    private BigDecimal uitVigente;

    @DecimalMin(value = "0.01", message = "La tasa IR4ta debe ser mayor a cero.")
    @DecimalMax(value = "100", message = "La tasa IR4ta no puede superar 100%.")
    private BigDecimal tasaIr4ta;

    @DecimalMin(value = "0", message = "La base inafecta no puede ser negativa.")
    private BigDecimal baseInafectaIr4ta;

    @NotBlank(message = "La fuente oficial es obligatoria.")
    @Size(max = 500)
    private String fuenteOficial;

    @Size(max = 1000)
    private String urlFuenteOficial;

    private LocalDate fechaPublicacion;

    @Size(max = 200)
    private String observacion;

    // ── V010_93 — Límites de suspensión y reglas (opcionales en el input) ───
    @DecimalMin(value = "0", message = "El tope general no puede ser negativo.")
    private BigDecimal topeAnualGeneral;

    @DecimalMin(value = "0", message = "El tope director no puede ser negativo.")
    private BigDecimal topeAnualDirector;

    private Boolean aplicaCasGeneral;
    private Boolean aplicaCasDirector;

    @DecimalMin(value = "0", message = "El % de alerta preventiva no puede ser negativo.")
    @DecimalMax(value = "100", message = "El % de alerta preventiva no puede superar 100.")
    private BigDecimal pctAlertaPrev;

    @DecimalMin(value = "0", message = "El % de alerta crítica no puede ser negativo.")
    @DecimalMax(value = "100", message = "El % de alerta crítica no puede superar 100.")
    private BigDecimal pctAlertaCrit;

    @Size(max = 10)
    private String codigoSunatPlame;

    private Boolean flgCalcAcumulado;
    private Boolean flgAlerta80;
    private Boolean flgAlerta90;
    private Boolean flgMarcarValidacion;
    private Boolean flgRetencionAuto;
}
