package com.indeci.rrhh.dto;

import lombok.Data;

/** Request para crear un lote de proceso masivo. */
@Data
public class MetaPptoLoteDto {

    private Integer anioOrigen;
    private Integer anioDestino;
    /** CARGA_CATALOGO | COPIA_ANIO_ANTERIOR | APLICACION_EQUIVALENCIAS | IMPORTACION_EXCEL | PUBLICACION_ANUAL | REGULARIZACION */
    private String tipoProceso;
    private String observacion;
    private String archivoOrigen;
}
