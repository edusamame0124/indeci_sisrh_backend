package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 012 / C1 · P-05 — Monto certificado de una meta presupuestal,
 * tal como lo transcribe Tesorería de la certificación física.
 *
 * <p>Entrada de {@code PUT /api/rrhh/meta-presupuestal/{periodoId}}.
 */
@Data
public class MetaCertificacionDto {

    private String meta;

    private String centroCosto;

    private String fuenteFinanc;

    /** Techo certificado para la meta. */
    private Double montoCertificado;
}
