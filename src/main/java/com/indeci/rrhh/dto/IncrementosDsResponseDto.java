package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado del cálculo de incrementos DS para la UI de configuración remunerativa.
 *
 * <p>{@code remuneracionMensual} = {@code montoContrato} + {@code totalIncrementos}
 * cuando {@code aplica}; si no aplica, ambos totales coinciden con el monto contrato.</p>
 */
public record IncrementosDsResponseDto(
        boolean aplica,
        BigDecimal montoContrato,
        List<IncrementoDsItemDto> incrementos,
        BigDecimal totalIncrementos,
        BigDecimal remuneracionMensual) {

    public static IncrementosDsResponseDto sinIncrementos(BigDecimal montoContrato) {
        return new IncrementosDsResponseDto(
                false,
                montoContrato,
                List.of(),
                BigDecimal.ZERO.setScale(2),
                redondear(montoContrato));
    }

    static BigDecimal redondear(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return valor.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
