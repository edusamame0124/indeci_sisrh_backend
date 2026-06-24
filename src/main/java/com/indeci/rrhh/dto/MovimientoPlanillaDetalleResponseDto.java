package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class MovimientoPlanillaDetalleResponseDto {

    private Long id;

    private Long conceptoPlanillaId;

    /**
     * SPEC_CONCEPTOS_PLANILLA P3 — código del concepto. Prefiere el snapshot del
     * detalle (histórico inmutable); si es null (fila previa a V010_99), cae al
     * código vivo del concepto.
     */
    private String codigoConcepto;

    /** Nombre del concepto. Snapshot histórico con fallback al nombre vivo. */
    private String concepto;

    /** Tipo del concepto. Snapshot histórico con fallback al tipo vivo. */
    private String tipoConcepto;

    private Double monto;

    private Double cantidad;

    private String observacion;
}