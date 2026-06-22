package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * Un incremento DS mensual (solo lectura UI; fuente: parámetro remunerativo).
 */
public record IncrementoDsItemDto(
        String codigoParametro,
        String etiquetaDs,
        BigDecimal montoMensual) {
}
