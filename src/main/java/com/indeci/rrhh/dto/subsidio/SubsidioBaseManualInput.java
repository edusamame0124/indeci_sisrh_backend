package com.indeci.rrhh.dto.subsidio;

import java.util.List;

/**
 * Alta/edición manual (o MIXTA) de la base histórica de un caso de subsidio:
 * las filas mensuales de la ventana + un sustento obligatorio (es dinero
 * ingresado a mano, requiere trazabilidad).
 */
public record SubsidioBaseManualInput(
        List<SubsidioBaseDetalleInput> detalles,
        String observacion) {}
