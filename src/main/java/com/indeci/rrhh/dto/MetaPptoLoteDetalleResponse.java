package com.indeci.rrhh.dto;

import lombok.Data;

/** Fila de detalle de validación dentro de un lote masivo. */
@Data
public class MetaPptoLoteDetalleResponse {

    private Long id;
    private Long loteId;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDni;
    private Long metaOrigenId;
    private String metaOrigenCodigo;
    private Long metaDestinoId;
    private String metaDestinoCodigo;
    private String metaDestinoDescripcion;
    private Long empMetaAnualId;
    /** OK | OBSERVADO | SIN_EQUIVALENCIA | META_DESTINO_INACTIVA | EMPLEADO_INACTIVO | DUPLICADO | ERROR */
    private String estadoValidacion;
    private String mensajeValidacion;
    private String accionSugerida;
}
