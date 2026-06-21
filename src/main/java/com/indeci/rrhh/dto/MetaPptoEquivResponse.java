package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Response de una equivalencia entre metas de distintos años. */
@Data
public class MetaPptoEquivResponse {

    private Long id;
    private Integer anioOrigen;
    private Long metaOrigenId;
    private String metaOrigenCodigo;
    private String metaOrigenDescripcion;
    private Integer anioDestino;
    private Long metaDestinoId;
    private String metaDestinoCodigo;
    private String metaDestinoDescripcion;
    private String estado;
    private Integer activo;
    private String observacion;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private String motivoAnulacion;
}
