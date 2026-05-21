package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 010 / M13 — Request para revisar una conciliación AIRHSP.
 * estado: CONCILIADO | JUSTIFICADO | RECHAZADO.
 * justificacion es obligatoria para JUSTIFICADO y RECHAZADO.
 */
@Data
public class ConciliacionRevisionDto {

    private String estado;
    private String justificacion;
    private Long usuarioRevisa;
}
