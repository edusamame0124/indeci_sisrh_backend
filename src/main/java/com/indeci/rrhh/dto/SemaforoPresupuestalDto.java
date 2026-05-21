package com.indeci.rrhh.dto;

import java.util.List;

import lombok.Data;

/**
 * Spec 012 / C1 · P-05 — Semáforo presupuestal de un período: el desglose por
 * meta más los totales y el estado global.
 *
 * <p>Respuesta de {@code GET /api/rrhh/meta-presupuestal/semaforo/{periodoId}}.
 */
@Data
public class SemaforoPresupuestalDto {

    private Long periodoId;

    private String periodo;

    private List<SemaforoMetaDto> metas;

    private Double totalCertificado;

    private Double totalComprometido;

    /** VERDE si ninguna meta supera su techo; ROJO si al menos una lo hace. */
    private String estadoGlobal;
}
