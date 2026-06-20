package com.indeci.rrhh.dto;

import lombok.Data;

/** Request para crear una equivalencia entre metas de distintos años. */
@Data
public class MetaPptoEquivDto {

    private Integer anioOrigen;
    private Long metaOrigenId;
    private Integer anioDestino;
    private Long metaDestinoId;
    private String observacion;
}
