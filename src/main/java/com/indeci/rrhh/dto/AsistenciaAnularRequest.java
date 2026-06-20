package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * F5 / P4 — anulación controlada de una importación. El {@code motivo} es obligatorio.
 */
@Data
public class AsistenciaAnularRequest {

    private String motivo;
}
