package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 010 / M13 — Request para registrar una conciliación AIRHSP.
 * La DIFERENCIA no se envía: es columna VIRTUAL en BD (MONTO_SISTEMA − MONTO_AIRHSP).
 */
@Data
public class ConciliacionAirhspDto {

    private Long empleadoId;
    private Long movimientoPlanillaId;
    private Long periodoPlanillaId;
    private Double montoSistema;
    private Double montoAirhsp;
}
