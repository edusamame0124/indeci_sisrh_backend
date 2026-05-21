package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 012 / C1 · P-05 — Fila del semáforo presupuestal: una meta con su
 * certificado (techo) contra lo comprometido (suma de netos de la planilla).
 */
@Data
public class SemaforoMetaDto {

    private String meta;

    private String centroCosto;

    private String fuenteFinanc;

    /** Cantidad de empleados de la meta en la planilla del período (PEA). */
    private Integer pea;

    /** Techo certificado. 0 si la meta aún no tiene certificación cargada. */
    private Double montoCertificado;

    /** Suma de netos de la planilla del período para la meta. */
    private Double montoComprometido;

    /** montoCertificado − montoComprometido (negativo = sobregiro). */
    private Double saldo;

    /** VERDE si comprometido ≤ certificado; ROJO si lo supera. */
    private String estado;
}
