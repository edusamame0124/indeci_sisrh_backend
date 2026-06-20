package com.indeci.rrhh.dto;

import lombok.Data;

/** Resolución de una fila observada en el lote (el usuario elige la meta destino correcta). */
@Data
public class MetaResolverDto {

    private Long loteDetId;
    private Long metaDestinoId;
    private String observacion;
}
