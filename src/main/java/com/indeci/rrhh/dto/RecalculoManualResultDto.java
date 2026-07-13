package com.indeci.rrhh.dto;

import java.util.List;

/** Resultado de recalcular TODO el saldo vacacional de un empleado (botón "Provisionar Auto"). */
public record RecalculoManualResultDto(
        List<CorreccionSaldoDto> cambios,
        int sinCambios) {
}
