package com.indeci.rrhh.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EmpleadoOtrosIngresosDto {

    private Long id;

    @NotNull(message = "El ID del empleado es obligatorio")
    private Long empleadoId;

    @NotNull(message = "El año fiscal es obligatorio")
    private Integer anioFiscal;

    @NotNull(message = "El monto de ingresos es obligatorio")
    @Min(value = 0, message = "El monto de ingresos no puede ser negativo")
    private BigDecimal montoIngresos;

    @NotNull(message = "El monto de retenciones es obligatorio")
    @Min(value = 0, message = "El monto de retenciones no puede ser negativo")
    private BigDecimal montoRetenciones;
}
