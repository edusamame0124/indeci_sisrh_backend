package com.indeci.rrhh.dto.subsidio;

import java.math.BigDecimal;

/**
 * Una fila editable de la base histórica manual/MIXTA (un mes de la ventana).
 *
 * <p>{@code remuneracionReal} es lo efectivamente pagado ese mes (0 si LSGR
 * total, parcial si LSGR parcial). La {@code incidencia} es solo una etiqueta de
 * trazabilidad — NO fuerza el monto. El tope por año se aplica en el servicio;
 * los overrides permiten casos excepcionales sustentados.</p>
 */
public record SubsidioBaseDetalleInput(
        String periodo,
        BigDecimal remuneracionReal,
        String incidencia,
        BigDecimal topeAplicadoOverride,
        BigDecimal baseComputableOverride) {}
